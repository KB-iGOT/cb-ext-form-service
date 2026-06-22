package com.karmayogi.form.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmayogi.form.entity.FormQuestions;
import com.karmayogi.form.entity.Forms;
import com.karmayogi.form.model.FieldMeta;
import com.karmayogi.form.model.FieldUpdateResult;
import com.karmayogi.form.model.FormRequest;
import com.karmayogi.form.repository.FormQuestionsRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author anil
 */
@Component
@RequiredArgsConstructor
public class FormUpdateHelper {

    private static final Logger log = LoggerFactory.getLogger(FormUpdateHelper.class);

    private final FormEntityMapper formEntityMapper;
    private final FormQuestionsRepository formQuestionsRepository;
    private final ObjectMapper objectMapper;


    public FieldUpdateResult updateFields(Forms form, FormRequest request) {
        if (CollectionUtils.isEmpty(request.getFields())) {
            return new FieldUpdateResult(0, 0, 0, new HashSet<>());
        }
        boolean isV2 = form.getQuestions() != null;
        return isV2
                ? handleV2FieldUpdate(form, request)
                : handleV1FieldUpdate(form, request);
    }

    private FieldUpdateResult handleV2FieldUpdate(Forms form, FormRequest request) {
        List<FieldMeta> incoming = request.getFields();

        List<FieldMeta> existing = new ArrayList<>();
        if (form.getQuestions() != null) {
            existing = objectMapper.convertValue(form.getQuestions(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<FieldMeta>>() {});
        }

        Set<String> existingIds = existing.stream()
                .map(FieldMeta::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        int created = 0;
        int updated = 0;
        Set<String> incomingIds = new HashSet<>();
        for (FieldMeta f : incoming) {
            if (StringUtils.isNotBlank(f.getId()) && existingIds.contains(f.getId())) updated++;
            else created++;
            if (f.getId() != null) incomingIds.add(f.getId());
        }
        int deleted = (int) existingIds.stream()
                .filter(id -> !incomingIds.contains(id)).count();

        formEntityMapper.populateFieldIds(incoming);
        form.setQuestions(incoming);
        return new FieldUpdateResult(created, updated, deleted, new HashSet<>());
    }

    private FieldUpdateResult handleV1FieldUpdate(Forms form, FormRequest request) {
        String formId = form.getFormId();

        List<FormQuestions> existing =
                formQuestionsRepository.findByFormIdOrderByQuestionOrderAsc(formId);
        Set<String> existingIds = existing.stream()
                .map(FormQuestions::getQuestionId)
                .collect(Collectors.toSet());

        Set<String> incomingIds = new HashSet<>();
        List<FormQuestions> toSave = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;

        for (FieldMeta field : request.getFields()) {
            String fieldId = StringUtils.isNotBlank(field.getId())
                    ? field.getId()
                    : UUID.randomUUID().toString();
            field.setId(fieldId);
            incomingIds.add(fieldId);

            toSave.add(formEntityMapper
                    .toFormQuestionsEntity(field, formId, request.getStatus()));

            if (existingIds.contains(fieldId)) updatedCount++;
            else createdCount++;
        }
        Set<String> staleIds = new HashSet<>(existingIds);
        staleIds.removeAll(incomingIds);
        if (!staleIds.isEmpty()) {
            formQuestionsRepository.deleteAllById(staleIds);
            log.info("FormUpdateHelper deleted stale questions formId={} count={}",
                    formId, staleIds.size());
        }

        formQuestionsRepository.saveAll(toSave);
        log.info("FormUpdateHelper v1 questions saved formId={} created={} updated={}",
                formId, createdCount, updatedCount);

        return new FieldUpdateResult(createdCount, updatedCount,
                staleIds.size(), staleIds);
    }


}

