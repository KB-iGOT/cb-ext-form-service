package com.karmayogi.form.utils;

import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.entity.FormSubmission;
import com.karmayogi.form.entity.PublicFormSubmission;
import com.karmayogi.form.model.FeedbackRequest;
import com.karmayogi.form.model.FormSubmissionRequest;
import com.karmayogi.form.model.QuestionResponse;
import com.karmayogi.form.model.SearchCriteria;
import com.karmayogi.form.repository.FormSubmissionRepository;
import com.karmayogi.form.repository.FormSubmissionSpecification;
import com.karmayogi.form.repository.PublicFormSubmissionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.karmayogi.form.utils.Constants.*;

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
    private final EntityManager entityManager;
    private final FormConfig formConfig;


    public String validate(FormSubmissionRequest request,
                           String userId,
                           Map<String, Object> userInfo,
                           boolean isAnonymousUser) {
        if (!isAnonymousUser) {
            if (StringUtils.isBlank(userId)) {
                return ERR_INVALID_USER_ID;
            }
            try {
                Map<String, Object> userData = userUtils.getUsersReadData(userId);
                if (MapUtils.isEmpty(userData)) {
                    return ERR_INVALID_USER_ID;
                }
                userInfo.put(Constants.FULL_NAME,
                        StringUtils.defaultString(
                                (String) userData.get(Constants.FIRST_NAME)));
            } catch (Exception e) {
                log.error("validate user lookup failed userId={}: {}", userId, e.getMessage());
                return ERR_INVALID_USER_ID;
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
            if (Constants.ASSIGNMENT.equalsIgnoreCase(request.getContextType())) {
                return null;
            }
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

    public Map<String, Object> findSavedForm(String formId, String status,
                                             String userId, String contextId) {
        Optional<FormSubmission> result = StringUtils.isBlank(status)
                ? formSubmissionRepository.findLatestByFormIdAndUserIdAndContextId(
                formId, userId, StringUtils.defaultString(contextId))
                : formSubmissionRepository.findLatestByFormIdAndUserIdAndContextIdAndStatus(
                formId, userId, StringUtils.defaultString(contextId), status);

        return result.map(submission -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put(FIELD_SUBMISSION_ID,   submission.getSubmissionId());
            data.put(FORM_ID,         submission.getFormId());
            data.put(STATUS,         submission.getStatus());
            data.put(FIELD_SUBMITTED_BY,    submission.getSubmittedBy());
            data.put(FIELD_SUBMITTED_DATE,  submission.getSubmittedDate());
            data.put(CONTEXT_ID,      submission.getContextId());
            data.put(CONTEXT_NAME,    submission.getContextName());
            data.put(FIELD_CONTEXT_TYPE,    submission.getContextType());
            data.put(VERSION,        submission.getVersion());
            data.put(RESPONSES,      submission.getResponses());
            data.put(SUBMISSION_META, submission.getSubmissionMeta());
            return data;
        }).orElse(null);
    }

    public String validateGetSavedForm(String formId, String contextId,
                                       String userId) {
        if (StringUtils.isBlank(formId))    return ERR_FORM_ID_REQUIRED;
        if (StringUtils.isBlank(contextId)) return ERR_CONTEXT_ID_MISSING;
        if (StringUtils.isBlank(userId))    return ERR_INVALID_USER_ID;
        try {
            Map<String, Object> userData = userUtils.getUsersReadData(userId);
            if (MapUtils.isEmpty(userData)) return ERR_INVALID_USER_ID;
        } catch (Exception e) {
            log.error("validateGetSavedForm user lookup failed userId={}: {}", userId, e.getMessage());
            return ERR_INVALID_USER_ID;
        }
        return null;
    }

    public List<Map<String, Object>> getAllApplications(String formId) {
        return formSubmissionRepository.findAllSubmittedByFormId(formId)
                .stream()
                .map(s -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("submissionId",   s.getSubmissionId());
                    data.put("formId",         s.getFormId());
                    data.put("userId",         s.getUserId());
                    data.put("status",         s.getStatus());
                    data.put("fullName",       s.getFullName());
                    data.put("submittedBy",    s.getSubmittedBy());
                    data.put("submittedDate",  s.getSubmittedDate());
                    data.put("contextId",      s.getContextId());
                    data.put("contextName",    s.getContextName());
                    data.put("contextType",    s.getContextType());
                    data.put("version",        s.getVersion());
                    data.put("responses",      s.getResponses());
                    data.put("submissionMeta", s.getSubmissionMeta());
                    return data;
                })
                .toList();
    }

    public Map<String, Object> searchUserFeedbackForms(
            SearchCriteria criteria,
            List<String> hideCreatorContextTypes) {

        org.springframework.data.domain.Page<FormSubmission> page = fetchSubmissions(criteria);
        List<Map<String, Object>> records = mapAndHidePII(page.getContent(), hideCreatorContextTypes);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put(Constants.COUNT,   page.getTotalElements());
        content.put(Constants.CONTENT, records);
        if (StringUtils.isNotBlank(criteria.getGroupBy())) {
            content.put("groupedData",
                    buildGroupBy(criteria.getGroupBy(), criteria.getFilters()));
        }
        return content;
    }

    private List<Map<String, Object>> buildGroupBy(String groupBy,
                                                   Map<String, Object> filters) {
        try {
            String sql = buildGroupBySql(groupBy, filters);
            jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
            applyGroupByParams(query, filters);
            @SuppressWarnings("unchecked")
            List<Object[]> rows = query.getResultList();
            return mapGroupByRows(rows, groupBy);
        } catch (Exception e) {
            log.error("buildGroupBy error groupBy={}: {}", groupBy, e.getMessage());
            return Collections.emptyList();
        }
    }

    private void applyGroupByParams(jakarta.persistence.Query query,
                                    Map<String, Object> filters) {
        if (MapUtils.isEmpty(filters)) return;
        filters.entrySet().stream()
                .filter(e -> !FormSubmissionSpecification.isDateKey(e.getKey()))
                .filter(e -> !(e.getValue() instanceof List))
                .forEach(e -> query.setParameter(e.getKey(), e.getValue()));
    }

    private List<Map<String, Object>> mapGroupByRows(List<Object[]> rows,
                                                     String groupBy) {
        return rows.stream()
                .map(row -> mapGroupByRow(row, groupBy))
                .toList();
    }

    private Map<String, Object> mapGroupByRow(Object[] row, String groupBy) {
        Map<String, Object> bucket = new LinkedHashMap<>();
        bucket.put(groupBy, row[0]);
        bucket.put(Constants.COUNT, row[1]);
        if (Constants.CONTEXT_ID.equalsIgnoreCase(groupBy) && row.length > 2) {
            bucket.put(Constants.CONTEXT_NAME, row[2]);
        }
        return bucket;
    }

    private String buildGroupBySql(String groupBy, Map<String, Object> filters) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(toColumnName(groupBy)).append(", COUNT(*) as count ");
        if (Constants.CONTEXT_ID.equalsIgnoreCase(groupBy)) {
            sql.append(", contextname ");
        }
        sql.append("FROM form_submissions WHERE 1=1 ");
        if (MapUtils.isNotEmpty(filters)) {
            filters.keySet().stream()
                    .filter(k -> !FormSubmissionSpecification.isDateKey(k))
                    .forEach(k -> sql.append("AND ").append(toColumnName(k))
                            .append(" = :").append(k).append(" "));
        }
        sql.append("GROUP BY ").append(toColumnName(groupBy));
        if (Constants.CONTEXT_ID.equalsIgnoreCase(groupBy)) {
            sql.append(", contextname");
        }
        return sql.toString();
    }

    private void hideUserIdentifyingFields(Map<String, Object> userData) {
        userData.remove(Constants.SUBMITTED_BY);
        userData.remove(Constants.UPDATED_BY);
        userData.remove(Constants.CREATED_BY);
        userData.remove(Constants.FULL_NAME);
    }

    private String toColumnName(String key) {
        return switch (key) {
            case Constants.FIELD_FORM_ID        -> Constants.COL_FORM_ID;
            case Constants.FIELD_SUBMITTED_BY   -> Constants.COL_SUBMITTED_BY;
            case Constants.FIELD_UPDATED_BY     -> Constants.COL_UPDATED_BY;
            case Constants.FIELD_CONTEXT_ID     -> Constants.COL_CONTEXT_ID;
            case Constants.FIELD_CONTEXT_TYPE   -> Constants.COL_CONTEXT_TYPE;
            case Constants.FIELD_CONTEXT_ORG_ID -> Constants.COL_CONTEXT_ORG_ID;
            case Constants.FIELD_SUBMITTED_DATE -> Constants.COL_SUBMITTED_DATE;
            case Constants.FIELD_UPDATED_DATE   -> Constants.COL_UPDATED_DATE;
            default                             -> key.toLowerCase();
        };
    }

    private String toEntityField(String key) {
        return switch (key) {
            case Constants.FIELD_SUBMITTED_BY   -> Constants.FIELD_SUBMITTED_BY;
            case Constants.FIELD_UPDATED_BY     -> Constants.FIELD_UPDATED_BY;
            case Constants.FIELD_CONTEXT_ID     -> Constants.FIELD_CONTEXT_ID;
            case Constants.FIELD_CONTEXT_TYPE   -> Constants.FIELD_CONTEXT_TYPE;
            case Constants.FIELD_CONTEXT_ORG_ID -> Constants.FIELD_CONTEXT_ORG_ID;
            case Constants.FIELD_SUBMITTED_DATE -> Constants.FIELD_SUBMITTED_DATE;
            case Constants.FIELD_UPDATED_DATE   -> Constants.FIELD_UPDATED_DATE;
            case Constants.FIELD_FORM_ID        -> Constants.FIELD_FORM_ID;
            default                             -> key;
        };
    }

    private Map<String, Object> toMap(FormSubmission s) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(Constants.FIELD_SUBMISSION_ID,   s.getSubmissionId());
        data.put(Constants.FIELD_FORM_ID,         s.getFormId());
        data.put(Constants.FIELD_USER_ID,         s.getUserId());
        data.put(Constants.STATUS,                s.getStatus());
        data.put(Constants.FULL_NAME,             s.getFullName());
        data.put(Constants.FIELD_SUBMITTED_BY,    s.getSubmittedBy());
        data.put(Constants.FIELD_SUBMITTED_DATE,  s.getSubmittedDate());
        data.put(Constants.FIELD_CONTEXT_ID,      s.getContextId());
        data.put(Constants.CONTEXT_NAME,          s.getContextName());
        data.put(Constants.FIELD_CONTEXT_TYPE,    s.getContextType());
        data.put(Constants.VERSION,               s.getVersion());
        data.put(Constants.RESPONSES,             s.getResponses());
        data.put(Constants.SUBMISSION_META,       s.getSubmissionMeta());
        return data;
    }

    public String validateSubmissionSearch(SearchCriteria criteria) {
        String filterError = validateFilters(criteria.getFilters());
        if (filterError != null) return filterError;
        return validatePagination(criteria);
    }

    private String validateFilters(Map<String, Object> filters) {
        if (MapUtils.isEmpty(filters)) return null;

        String invalidKeys = filters.keySet().stream()
                .filter(k -> !formConfig.getSubmissionAllowedFilterKeys().contains(k))
                .collect(Collectors.joining(", "));
        if (!invalidKeys.isEmpty())
            return "Unsupported filter fields: " + invalidKeys;

        return filters.entrySet().stream()
                .filter(e -> e.getValue() instanceof List<?>)
                .map(e -> validateListFilter(e.getKey(), (List<?>) e.getValue()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String validateListFilter(String key, List<?> list) {
        if (list.isEmpty())
            return "Filter '" + key + "' contains empty list";
        if (list.size() > formConfig.getMaxAllowedFilterListSize())
            return "Too many values for filter '" + key
                    + "' (max=" + formConfig.getMaxAllowedFilterListSize() + ")";
        return null;
    }

    private String validatePagination(SearchCriteria criteria) {
        int page = criteria.getPage() != null && criteria.getPage() >= 0 ? criteria.getPage() : 0;
        if (page > formConfig.getSearchMaxPage())
            return "Maximum page number exceeded (max=" + formConfig.getSearchMaxPage() + ")";
        int size = criteria.getSize() != null && criteria.getSize() >= 0 ? criteria.getSize() : 20;
        if (size > formConfig.getSearchMaxPageSize())
            return "Maximum page size exceeded (max=" + formConfig.getSearchMaxPageSize() + ")";

        return null;
    }

    private org.springframework.data.domain.Page<FormSubmission> fetchSubmissions(
            SearchCriteria criteria) {
        org.springframework.data.jpa.domain.Specification<FormSubmission> spec =
                FormSubmissionSpecification.fromFilters(criteria.getFilters());
        String sortField = StringUtils.isNotBlank(criteria.getSortBy())
                ? toEntityField(criteria.getSortBy()) : "submittedDate";
        org.springframework.data.domain.Sort sort =
                Constants.DESC.equalsIgnoreCase(criteria.getSortOrder())
                        ? org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, sortField)
                        : org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.ASC, sortField);
        int page = criteria.getPage() != null && criteria.getPage() >= 0
                ? criteria.getPage() : 0;
        int size = criteria.getSize() != null && criteria.getSize() > 0
                ? criteria.getSize() : 20;
        return formSubmissionRepository.findAll(spec,
                org.springframework.data.domain.PageRequest.of(page, size, sort));
    }

    private List<Map<String, Object>> mapAndHidePII(List<FormSubmission> submissions, List<String> hideCreatorContextTypes) {
        Map<String, String> formIdContextMap = new HashMap<>();
        List<Map<String, Object>> records = submissions.stream()
                .map(s -> {
                    Map<String, Object> submission = toMap(s);
                    if (s.getFormId() != null && s.getContextType() != null) {
                        formIdContextMap.put(s.getFormId(), s.getContextType());
                    }
                    return submission;
                })
                .toList();

        if (CollectionUtils.isNotEmpty(hideCreatorContextTypes)) {
            records.forEach(submission -> {
                String contextType = formIdContextMap.get(String.valueOf(submission.get(Constants.FORM_ID)));
                if (contextType != null && hideCreatorContextTypes.contains(contextType)) {
                    hideUserIdentifyingFields(submission);
                }
            });
        }
        return records;
    }

    public FormSubmission saveFeedbackToSubmission(FeedbackRequest request, String userId) {
        Optional<FormSubmission> existing =
                formSubmissionRepository.findLatestByFormIdAndUserIdAndContextId(
                        request.getFormId(),
                        request.getSubmittedBy(),
                        StringUtils.defaultString(request.getContextId()));

        if (existing.isEmpty()) return null;

        FormSubmission submission = existing.get();

        // only SUBMITTED status allowed for feedback
        if (!Constants.SUBMITTED_CAPS.equalsIgnoreCase(submission.getStatus())) {
            throw new IllegalStateException(
                    "Invalid status. Only SUBMITTED assignment allowed for feedback. formId="
                            + request.getFormId());
        }

        // determine new status
        String status = StringUtils.isNotBlank(request.getStatus())
                && "Evaluated".equalsIgnoreCase(request.getStatus())
                ? request.getStatus().toUpperCase()
                : Constants.EVALUATED;

        // build submissionMeta
        Map<String, Object> submissionMeta = new LinkedHashMap<>();
        submissionMeta.put(Constants.TOTAL_MARKS_GIVEN,      request.getMarksGiven());
        submissionMeta.put(Constants.MAXIMUM_MARKS,          request.getMaximumMarks());
        submissionMeta.put(Constants.INSTRUCTOR_FEEDBACK,    request.getInstructorFeedback());
        submissionMeta.put(Constants.INSTRUCTOR_ID,          userId);

        submission.setStatus(status);
        submission.setUpdatedBy(userId);
        submission.setUpdatedDate(System.currentTimeMillis());
        submission.setSubmissionMeta(submissionMeta);

        formSubmissionRepository.save(submission);
        log.info("saveFeedbackToSubmission saved submissionId={} status={}",
                submission.getSubmissionId(), status);
        return submission;
    }

    public String validateFeedback(FeedbackRequest request, String userId) {
        try {
            Map<String, Object> userData = userUtils.getUsersReadData(userId);
            if (MapUtils.isEmpty(userData))
                return Constants.ERR_INVALID_USER_ID;
        } catch (Exception e) {
            log.error("validateFeedback user lookup failed userId={}: {}", userId, e.getMessage());
            return Constants.ERR_INVALID_USER_ID;
        }
        if (StringUtils.isBlank(request.getFormId()))
            return Constants.ERR_FORM_ID_MISSING;
        if (StringUtils.isBlank(request.getContextId()))
            return Constants.ERR_CONTEXT_ID_MISSING;
        return null;
    }

}
