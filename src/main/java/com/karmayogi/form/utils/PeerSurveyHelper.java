package com.karmayogi.form.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmayogi.form.config.cassandrautils.CassandraOperation;
import com.karmayogi.form.entity.FormQuestions;
import com.karmayogi.form.entity.Forms;
import com.karmayogi.form.model.FieldMeta;
import com.karmayogi.form.model.FormRequest;
import com.karmayogi.form.repository.FormQuestionsRepository;
import com.karmayogi.form.repository.FormRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * @author anil
 */
@Component
@RequiredArgsConstructor
public class PeerSurveyHelper {

    private static final Logger log = LoggerFactory.getLogger(PeerSurveyHelper.class);

    private final FormRepository formsJpaRepository;
    private final ObjectMapper objectMapper;
    private final CassandraOperation cassandraOperation;
    private final FormQuestionsRepository formQuestionsRepository;

    public boolean peerSurveyExists(String identifier, String orgId,
                                    List<String> statuses) {
        List<Forms> existing = formsJpaRepository.findExistingPeerSurveys(
                Constants.PEER_VALIDATION_SURVEY, orgId, identifier, statuses);
        return CollectionUtils.isNotEmpty(existing);
    }

    public List<FieldMeta> getPeerEvaluationCommonQuestions() {
        try {
            Map<String, Object> queryParams = Collections.singletonMap(
                    Constants.ID, Constants.PEER_VALIDATION_SURVEY);
            List<Map<String, Object>> defaultQuestions =
                    cassandraOperation.getRecordsByPropertiesByKey(
                            Constants.KEYSPACE_SUNBIRD,
                            Constants.SYSTEM_SETTINGS,
                            queryParams, null, null);

            if (CollectionUtils.isEmpty(defaultQuestions)) return Collections.emptyList();

            String value = (String) defaultQuestions.get(0).get(Constants.VALUE);
            return Arrays.asList(objectMapper.readValue(value, FieldMeta[].class));

        } catch (Exception e) {
            log.error("getPeerEvaluationCommonQuestions failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }


    public void mergeSystemGeneratedQuestions(FormRequest request) {
        List<FieldMeta> commonQuestions = getPeerEvaluationCommonQuestions();
        if (CollectionUtils.isEmpty(commonQuestions)) return;

        List<FieldMeta> existingFields = request.getFields();
        if (existingFields == null) existingFields = new ArrayList<>();

        int maxOrder = existingFields.stream()
                .map(FieldMeta::getOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);

        for (FieldMeta field : commonQuestions) {
            maxOrder++;
            field.setOrder(maxOrder);
            existingFields.add(field);
        }
        request.setFields(existingFields);
    }


    public Long parseEndDate(String endDate) {
        try {
            return Instant.parse(endDate).toEpochMilli();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid endDate format");
        }
    }


    public String validatePeerSurveyUpdate(FormRequest form) {
        if (form == null) return "Request is required";
        if (StringUtils.isBlank(form.getTitle()))
            return Constants.ERR_TITLE_MISSING;
        if (form.getClientVersion() == null || form.getClientVersion() <= 0.0f)
            return Constants.ERR_INVALID_CLIENT_VERSION;

        int triggerAfter = 30;
        Map<String, Object> additionalProps = form.getAdditionalProperties();
        if (MapUtils.isNotEmpty(additionalProps)) {
            Object triggerObj = additionalProps.get(Constants.TRIGGER_AFTER);
            if (triggerObj != null) {
                try {
                    triggerAfter = Integer.parseInt(triggerObj.toString());
                    if (triggerAfter < 30 || triggerAfter > 60 || triggerAfter % 5 != 0)
                        return Constants.PEER_SURVEY_TRIGGER_AFTER_RANGE_INVALID;
                } catch (Exception e) {
                    return Constants.PEER_SURVEY_TRIGGER_AFTER_INVALID;
                }
            }
            Object lookBackObj = additionalProps.get(Constants.COMPLETION_LOOK_BACK);
            if (lookBackObj != null && triggerObj != null) {
                try {
                    int lookBack = Integer.parseInt(lookBackObj.toString());
                    if (lookBack < triggerAfter)
                        return Constants.PEER_SURVEY_LOOKBACK_LESS_THAN_TRIGGER;
                } catch (Exception e) {
                    return Constants.PEER_SURVEY_LOOKBACK_INVALID;
                }
            }
        } else {
            return Constants.PEER_SURVEY_INVALID_ADDITIONAL_PROPERTY;
        }

        if (StringUtils.isBlank(form.getEndDate()))
            return Constants.PEER_SURVEY_END_DATE_MISSING;

        try {
            Instant endDate     = Instant.parse(form.getEndDate());
            Instant minAllowed  = Instant.now().plus(triggerAfter,
                    java.time.temporal.ChronoUnit.DAYS);
            if (endDate.isBefore(minAllowed))
                return Constants.PEER_SURVEY_END_DATE_BEFORE_ALLOWED;
        } catch (Exception e) {
            return Constants.PEER_SURVEY_END_DATE_INVALID;
        }

        List<com.karmayogi.form.model.FieldMeta> fields = form.getFields();
        if (!org.apache.commons.collections4.CollectionUtils.isEmpty(fields)) {
            long customCount = fields.stream()
                    .filter(f -> !Boolean.TRUE.equals(
                            org.apache.commons.collections4.MapUtils.getBoolean(
                                    f.getAdditionalProperties(),
                                    Constants.SYSTEM_GENERATED)))
                    .count();
            if (customCount > 2)
                return Constants.PEER_SURVEY_MAX_ADDITIONAL_QUESTIONS;

            for (com.karmayogi.form.model.FieldMeta field : fields) {
                if (StringUtils.isBlank(field.getFieldType()))
                    return Constants.PEER_SURVEY_FIELD_TYPE_MISSING;
                String fieldType = field.getFieldType().toLowerCase();
                if (!fieldType.equals(Constants.RADIO)
                        && !fieldType.equals(Constants.CHECKBOX)
                        && !fieldType.equals(Constants.TEXTAREA)
                        && !fieldType.equals(Constants.NUMERIC_RATING))
                    return Constants.PEER_SURVEY_FIELD_TYPE_INVALID;
                if (StringUtils.isBlank(field.getName()))
                    return Constants.PEER_SURVEY_QUESTION_NAME_MISSING;
            }
        }
        return null;
    }

    public String validateActiveSurveyOverlap(String identifier, String orgId,
                                              long incomingStart, long incomingEnd,
                                              String currentFormId) {
        List<Forms> surveys =
                formsJpaRepository.findExistingPeerSurveys(
                        Constants.PEER_VALIDATION_SURVEY, orgId, identifier,
                        List.of("Draft", "Active"));

        for (Forms survey : surveys) {
            // skip current form
            if (currentFormId != null && currentFormId.equals(survey.getFormId()))
                continue;

            if ("Draft".equalsIgnoreCase(survey.getStatus()))
                return Constants.PEER_SURVEY_ALREADY_EXISTS;

            Long existingStart = survey.getCreatedAt();
            Long existingEnd   = survey.getEndDate();
            if (existingStart != null && existingEnd != null) {
                boolean overlap = existingStart <= incomingEnd
                        && existingEnd >= incomingStart;
                if (overlap) return Constants.PEER_SURVEY_ALREADY_EXISTS;
            }
        }
        return null;
    }

    public String validateSystemGeneratedProtection(FormRequest request) {
        List<FieldMeta> incomingFields = request.getFields();

        // fetch existing fields from form_questions table → map to FieldMeta
        List<FormQuestions> existingEntities =
                formQuestionsRepository.findByFormIdOrderByQuestionOrderAsc(
                        request.getFormId());

        List<FieldMeta> existingFields = existingEntities.stream()
                .map(q -> {
                    FieldMeta f = new FieldMeta();
                    f.setId(q.getQuestionId());
                    f.setName(q.getName());
                    f.setFieldType(q.getFieldType());
                    f.setAdditionalProperties(q.getAdditionalProperties());
                    return f;
                })
                .toList();

        // count custom (non-system) questions in incoming
        long customCount = incomingFields.stream()
                .filter(f -> !Boolean.TRUE.equals(
                        MapUtils.getBoolean(f.getAdditionalProperties(),
                                Constants.SYSTEM_GENERATED)))
                .count();
        if (customCount > 2)
            return "Maximum 2 additional questions allowed";

        for (FieldMeta existing : existingFields) {
            boolean isSystem = Boolean.TRUE.equals(
                    MapUtils.getBoolean(existing.getAdditionalProperties(),
                            Constants.SYSTEM_GENERATED));
            if (!isSystem) continue;

            FieldMeta incoming = incomingFields.stream()
                    .filter(f -> existing.getId().equals(f.getId()))
                    .findFirst().orElse(null);

            if (incoming == null)
                return "System generated questions cannot be removed";
            if (!existing.getName().equals(incoming.getName())
                    || !existing.getFieldType().equals(incoming.getFieldType()))
                return "System generated questions cannot be modified";
        }
        return null;
    }

    public String fetchRecordsFromDB(String keyspace, String tableName, Map<String, Object> queryParams, List<String> fieldNames) {
        try {
            List<Map<String, Object>> records =
                    cassandraOperation.getRecordsByPropertiesByKey(
                            keyspace,
                            tableName,
                            queryParams,
                            fieldNames,
                            null);
            if (CollectionUtils.isEmpty(records)) return null;
            return (String) records.get(0).get(fieldNames.get(0));
        } catch (Exception e) {
            log.warn("fetchOrgName failed: {}", e.getMessage());
            return null;
        }
    }
}
