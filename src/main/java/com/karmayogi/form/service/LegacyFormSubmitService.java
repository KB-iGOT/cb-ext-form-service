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
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * v1 saveFormSubmit, as the old form-service handled it: answers come keyed by question text
 * (dataObject) and are converted to v2 responses before saving. NPS forms keep their legacy
 * flow in the old form-service, so they are forwarded there unchanged.
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
    private final RestTemplate restTemplate;

    public ResponseEntity<Object> submit(Map<String, Object> body, String userId, String userInfo) {
        Object formIdObj = body == null ? null : body.get(Constants.FORM_ID);
        if (formIdObj == null || StringUtils.isBlank(String.valueOf(formIdObj))) {
            return ResponseEntity.ok(failure("Invalid form id"));
        }
        String formId = String.valueOf(formIdObj).trim();

        if (isNpsForm(formId)) {
            return forwardToLegacyService(body, userId, userInfo);
        }

        Map<String, Object> dataObject = body.get(Constants.DATA_OBJECT) instanceof Map<?, ?> m
                ? objectMapper.convertValue(m, new TypeReference<Map<String, Object>>() {})
                : null;
        if (MapUtils.isEmpty(dataObject)) {
            return ResponseEntity.ok(failure("Form data object is empty"));
        }

        Optional<Forms> form = formRepository.findById(formId);
        if (form.isEmpty()) {
            log.error("legacy submit: form not available formId={}", formId);
            return ResponseEntity.ok(failure("Form is not available"));
        }

        FormSubmissionRequest request = toSubmissionRequest(formId, form.get(), dataObject, body);
        ApiResponse response = formService.submitForm(request, userId, false);

        if (Constants.STATUS_SUCCESS.equalsIgnoreCase(response.getParams().getStatus())) {
            return ResponseEntity.ok(success());
        }
        return ResponseEntity.ok(failure(response.getParams().getErrMsg()));
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

    private boolean isNpsForm(String formId) {
        return formConfig.getNpsFormIds() != null && formConfig.getNpsFormIds().contains(formId);
    }

    private ResponseEntity<Object> forwardToLegacyService(Map<String, Object> body, String userId, String userInfo) {
        String url = formConfig.getLegacyFormServiceUrl() + Constants.LEGACY_SAVE_FORM_SUBMIT_PATH;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.isNotBlank(userId)) headers.set(Constants.X_AUTHENTICATED_USER_ID, userId);
        if (StringUtils.isNotBlank(userInfo)) headers.set(Constants.X_USER_INFO, userInfo);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);
            log.info("legacy submit: NPS form forwarded to old form-service status={}", response.getStatusCode());
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (HttpStatusCodeException e) {
            log.error("legacy submit: NPS forward failed status={}: {}", e.getStatusCode(), e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("legacy submit: NPS forward failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(failure(Constants.LEGACY_PROCESS_FAILED));
        }
    }

    // response shape of the old form-service v1 API
    private Map<String, Object> success() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(Constants.LEGACY_STATUS_INFO, statusInfo(Constants.LEGACY_SUCCESS_CODE, Constants.STATUS_SUCCESS_LOWERCASE, ""));
        result.put(Constants.RESPONSE_DATA, Boolean.TRUE);
        return result;
    }

    private Map<String, Object> failure(String errorMessage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(Constants.LEGACY_STATUS_INFO, statusInfo(Constants.LEGACY_FAILURE_CODE,
                Constants.LEGACY_PROCESS_FAILED, StringUtils.defaultString(errorMessage)));
        return result;
    }

    private Map<String, Object> statusInfo(int code, String message, String errorMessage) {
        Map<String, Object> statusInfo = new LinkedHashMap<>();
        statusInfo.put(Constants.LEGACY_STATUS_CODE, code);
        statusInfo.put(Constants.LEGACY_STATUS_MESSAGE, message);
        statusInfo.put(Constants.LEGACY_ERROR_MESSAGE, errorMessage);
        return statusInfo;
    }
}
