package com.karmayogi.form.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.entity.Forms;
import com.karmayogi.form.model.FieldMeta;
import com.karmayogi.form.model.FormSubmissionRequest;
import com.karmayogi.form.model.QuestionResponse;
import com.karmayogi.form.repository.FormQuestionsRepository;
import com.karmayogi.form.repository.FormRepository;
import com.karmayogi.form.utils.ApiResponse;
import com.karmayogi.form.utils.Constants;
import com.karmayogi.form.utils.LegacyResponses;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * v1 saveFormSubmit, as the old form-service handled it: answers come keyed by question text
 * (dataObject) and are converted to v2 responses before saving. NPS forms keep their own
 * flow (NpsSubmissionService).
 *
 * @author anil
 */
@Service
@RequiredArgsConstructor
public class LegacyFormSubmitService {

    private static final Logger log = LoggerFactory.getLogger(LegacyFormSubmitService.class);

    private final FormService formService;
    private final FormRepository formRepository;
    private final FormQuestionsRepository formQuestionsRepository;
    private final FormConfig formConfig;
    private final ObjectMapper objectMapper;
    private final NpsSubmissionService npsSubmissionService;

    public ResponseEntity<Object> submit(Map<String, Object> body, String userId) {
        Object formIdObj = body == null ? null : body.get(Constants.FORM_ID);
        if (formIdObj == null || StringUtils.isBlank(String.valueOf(formIdObj))) {
            return ResponseEntity.ok(LegacyResponses.failure("Invalid form id"));
        }
        String formId = String.valueOf(formIdObj).trim();

        if (npsSubmissionService.isNpsForm(formId)) {
            return npsSubmissionService.submit(body, userId);
        }

        Map<String, Object> dataObject = body.get(Constants.DATA_OBJECT) instanceof Map<?, ?> m
                ? objectMapper.convertValue(m, new TypeReference<Map<String, Object>>() {})
                : null;
        if (MapUtils.isEmpty(dataObject)) {
            return ResponseEntity.ok(LegacyResponses.failure("Form data object is empty"));
        }

        Optional<Forms> form = formRepository.findById(formId);
        if (form.isEmpty()) {
            log.error("legacy submit: form not available formId={}", formId);
            return ResponseEntity.ok(LegacyResponses.failure("Form is not available"));
        }

        FormSubmissionRequest request = toSubmissionRequest(formId, form.get(), dataObject, body);
        ApiResponse response = formService.submitForm(request, userId, false);

        if (Constants.STATUS_SUCCESS.equalsIgnoreCase(response.getParams().getStatus())) {
            return ResponseEntity.ok(LegacyResponses.success(Boolean.TRUE));
        }
        return ResponseEntity.ok(LegacyResponses.failure(response.getParams().getErrMsg()));
    }

    private FormSubmissionRequest toSubmissionRequest(String formId, Forms form,
                                                      Map<String, Object> dataObject,
                                                      Map<String, Object> body) {
        List<QuestionResponse> responses = new ArrayList<>();
        for (FieldMeta field : questions(form)) {
            Object answer = dataObject.get(field.getName());
            responses.add(QuestionResponse.builder()
                    .questionId(field.getId())
                    .question(field.getName())
                    .answer(answer != null ? answer : "")
                    .answerType(String.valueOf(field.getFieldType()))
                    .build());
        }

        // "Course ID and Name" carries "<contextId>,<contextName>"
        String contextId = "";
        String contextName = "";
        Object courseIdAndName = dataObject.get(Constants.COURSE_ID_AND_NAME);
        if (courseIdAndName instanceof String value && StringUtils.isNotBlank(value)) {
            String[] parts = value.split(",", 2);
            contextId = parts[0].trim();
            contextName = parts.length > 1 ? parts[1].trim() : "";
        }

        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId(formId);
        request.setContextId(contextId);
        request.setContextName(contextName);
        request.setResponses(responses);
        request.setStatus(StringUtils.defaultIfBlank((String) body.get(Constants.STATUS), Constants.SUBMITTED_CAPS));
        if (body.get(Constants.VERSION) instanceof Number version) {
            request.setVersion(version.intValue());
        }
        return request;
    }

    // question fields only: heading/separator carry no answer
    private List<FieldMeta> questions(Forms form) {
        if (form.getQuestions() != null) {
            return objectMapper.convertValue(form.getQuestions(), new TypeReference<List<FieldMeta>>() {})
                    .stream()
                    .filter(f -> !formConfig.getNonQuestionFieldTypes().contains(f.getFieldType()))
                    .toList();
        }
        return formQuestionsRepository.findByFormIdOrderByQuestionOrderAsc(form.getFormId())
                .stream()
                .filter(q -> !formConfig.getNonQuestionFieldTypes().contains(q.getFieldType()))
                .map(q -> {
                    FieldMeta field = new FieldMeta();
                    field.setId(q.getQuestionId());
                    field.setName(q.getName());
                    field.setFieldType(q.getFieldType());
                    return field;
                })
                .toList();
    }
}
