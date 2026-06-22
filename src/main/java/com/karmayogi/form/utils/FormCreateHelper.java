package com.karmayogi.form.utils;

import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.entity.FormQuestions;
import com.karmayogi.form.entity.Forms;
import com.karmayogi.form.model.FormRequest;
import com.karmayogi.form.repository.FormQuestionsRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * @author anil
 */
@Component
@RequiredArgsConstructor
public class FormCreateHelper {

    private static final Logger log = LoggerFactory.getLogger(FormCreateHelper.class);

    private final FormEntityMapper formEntityMapper;
    private final FormQuestionsRepository formQuestionsRepository;
    private final FormConfig formConfig;

    /**
     * Prepares form entity for v2 (JSONB) — populates field ids and sets questions.
     */
    public void prepareV2Form(Forms form, FormRequest request, String formId) {
        form.setClientVersion(formConfig.getV2ClientVersion());
        if (CollectionUtils.isNotEmpty(request.getFields())) {
            request.getFields().forEach(field -> {
                if (StringUtils.isBlank(field.getId())) {
                    field.setId(UUID.randomUUID().toString());
                    field.setFormId(formId);
                }
            });
        }
        form.setQuestions(request.getFields());
        log.info("FormCreateHelper v2 JSONB prepared formId={}", formId);
    }

    /**
     * Prepares form entity for v1 (form_questions table) — sets questions to null.
     */
    public void prepareV1Form(Forms form) {
        form.setClientVersion(formConfig.getV1ClientVersion());
        form.setQuestions(null);
    }

    /**
     * Saves form_questions rows for v1 forms.
     * Returns saved questions for Kafka publish.
     * No-op for v2.
     */
    public List<FormQuestions> saveV1Questions(FormRequest request,
                                               String formId,
                                               boolean isV2) {
        if (isV2 || CollectionUtils.isEmpty(request.getFields())) {
            return Collections.emptyList();
        }
        List<FormQuestions> questions = formEntityMapper
                .toFormQuestionsEntities(request.getFields(), formId, request.getStatus());
        formQuestionsRepository.saveAll(questions);
        log.info("FormCreateHelper v1 questions saved formId={} count={}",
                formId, questions.size());
        return questions;
    }
}
