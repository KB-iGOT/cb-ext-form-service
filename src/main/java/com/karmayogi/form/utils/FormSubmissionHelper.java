package com.karmayogi.form.utils;

import com.karmayogi.form.entity.FormSubmission;
import com.karmayogi.form.entity.PublicFormSubmission;
import com.karmayogi.form.model.FormSubmissionRequest;
import com.karmayogi.form.model.QuestionResponse;
import com.karmayogi.form.repository.FormSubmissionRepository;
import com.karmayogi.form.repository.PublicFormSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * @author anil
 */
@Component
@RequiredArgsConstructor
public class FormSubmissionHelper {

    private static final Logger log = LoggerFactory.getLogger(FormSubmissionHelper.class);

    private final FormSubmissionRepository formSubmissionRepository;
    private final PublicFormSubmissionRepository publicFormSubmissionRepository;
    private final UserUtils userUtils;


    public String validate(FormSubmissionRequest request,
                           String userId,
                           Map<String, Object> userInfo,
                           boolean isAnonymousUser) {
        if (!isAnonymousUser) {
            if (StringUtils.isBlank(userId)) {
                return Constants.ERR_INVALID_USER_ID;
            }
            try {
                Map<String, Object> userData = userUtils.getUsersReadData(userId);
                if (MapUtils.isEmpty(userData)) {
                    return Constants.ERR_INVALID_USER_ID;
                }
                userInfo.put(Constants.FULL_NAME,
                        StringUtils.defaultString(
                                (String) userData.get(Constants.FIRST_NAME)));
            } catch (Exception e) {
                log.error("validate user lookup failed userId={}: {}", userId, e.getMessage());
                return Constants.ERR_INVALID_USER_ID;
            }
        }

        if (request == null) return "Request is required";

        if (StringUtils.isBlank(request.getFormId()))
            return Constants.ERR_FORM_ID_MISSING;

        if (StringUtils.isBlank(request.getStatus()))
            return Constants.ERR_STATUS_MISSING;

        String status = request.getStatus().toUpperCase();
        if (!Constants.DRAFT.equals(status) && !Constants.SUBMITTED_CAPS.equals(status))
            return Constants.ERR_INVALID_STATUS;

        if (request.getResponses() == null || request.getResponses().isEmpty()) {
            return Constants.DRAFT.equals(status) ? null : Constants.ERR_RESPONSES_MISSING;
        }

        if (!Constants.DRAFT.equals(status)) {
            int index = 0;
            for (QuestionResponse qr : request.getResponses()) {
                index++;
                if (qr == null)
                    return Constants.ERR_INVALID_USER_SUBMISSION + index;
                if (StringUtils.isBlank(qr.getQuestion()))
                    return Constants.ERR_QUESTION_TEXT_MISSING + index;
                if (StringUtils.isBlank(qr.getQuestionId()))
                    return Constants.ERR_QUESTION_ID_MISSING + index;
                if (StringUtils.isBlank(qr.getAnswerType()))
                    return Constants.ERR_ANSWER_TYPE_MISSING + qr.getQuestion();
            }
        }
        return null;
    }

    public List<Map<String, Object>> normalizeResponses(
            List<QuestionResponse> responses) {
        if (responses == null) return Collections.emptyList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (QuestionResponse qr : responses) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("question",   qr.getQuestion());
            normalized.put("questionId", qr.getQuestionId());
            normalized.put("answerType", qr.getAnswerType());
            normalized.put("answer",     getNormalizedAnswer(qr.getAnswer()));
            result.add(normalized);
        }
        return result;
    }

    private Object getNormalizedAnswer(Object answer) {
        if (answer == null) return "";
        if (answer instanceof String || answer instanceof Number
                || answer instanceof Boolean) {
            String str = String.valueOf(answer);
            return str.trim().isEmpty() ? "" : str;
        }
        if (answer instanceof List) {
            List<?> list = (List<?>) answer;
            boolean allPrimitive = list.stream().allMatch(
                    item -> item instanceof String
                            || item instanceof Number
                            || item instanceof Boolean);
            if (allPrimitive) {
                return list.stream()
                        .map(String::valueOf)
                        .map(String::trim)
                        .map(s -> s.isEmpty() ? "" : s)
                        .toList();
            }
            throw new IllegalArgumentException(
                    "Unsupported nested list type: only String, Number, Boolean allowed");
        }
        throw new IllegalArgumentException(
                "Unsupported answer type: " + answer.getClass().getName());
    }

    public String saveAuthenticated(FormSubmissionRequest request,
                                    String userId,
                                    String status,
                                    String contextType,
                                    List<Map<String, Object>> normalizedResponses,
                                    Map<String, Object> userInfo) {
        long now = System.currentTimeMillis();
        Optional<FormSubmission> existing =
                formSubmissionRepository.findLatestByFormIdAndUserIdAndContextId(
                        request.getFormId(), userId,
                        StringUtils.defaultString(request.getContextId()));

        FormSubmission submission;
        if (existing.isPresent()) {
            submission = existing.get();
            submission.setStatus(status);
            submission.setUpdatedBy(userId);
            submission.setUpdatedDate(now);
            log.info("saveAuthenticated updating submissionId={}", submission.getSubmissionId());
        } else {
            submission = new FormSubmission();
            submission.setSubmissionId(UUID.randomUUID().toString());
            submission.setFormId(request.getFormId());
            submission.setUserId(userId);
            submission.setContextId(request.getContextId());
            submission.setContextName(request.getContextName());
            submission.setContextType(contextType);
            submission.setContextOrgId(request.getContextOrgId());
            submission.setSubmitUrl(request.getSubmitUrl());
            submission.setVersion(request.getVersion());
            submission.setFullName(StringUtils.defaultString(
                    (String) userInfo.get(Constants.FULL_NAME)));
            submission.setSubmittedBy(userId);
            submission.setSubmittedDate(now);
            submission.setStatus(status);
            log.info("saveAuthenticated creating new submission formId={} userId={}",
                    request.getFormId(), userId);
        }

        submission.setResponses(normalizedResponses);
        submission.setSubmissionMeta(request.getSubmissionMeta());
        submission.setAttachments(request.getAttachments());

        formSubmissionRepository.save(submission);
        return submission.getSubmissionId();
    }

    public String saveAnonymous(FormSubmissionRequest request,
                                String status,
                                String contextType,
                                List<Map<String, Object>> normalizedResponses) {
        long now = System.currentTimeMillis();
        PublicFormSubmission submission = new PublicFormSubmission();
        submission.setSubmissionId(UUID.randomUUID().toString());
        submission.setFormId(request.getFormId());
        submission.setContextId(request.getContextId());
        submission.setContextName(request.getContextName());
        submission.setContextType(contextType);
        submission.setContextOrgId(request.getContextOrgId());
        submission.setVersion(request.getVersion());
        submission.setStatus(status);
        submission.setSubmittedDate(now);
        submission.setResponses(normalizedResponses);
        submission.setSubmissionMeta(request.getSubmissionMeta());

        publicFormSubmissionRepository.save(submission);
        log.info("saveAnonymous saved submissionId={}", submission.getSubmissionId());
        return submission.getSubmissionId();
    }

}
