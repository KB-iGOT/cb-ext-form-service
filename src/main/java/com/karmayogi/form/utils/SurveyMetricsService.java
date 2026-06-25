package com.karmayogi.form.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmayogi.form.config.cassandrautils.CassandraOperation;
import com.karmayogi.form.entity.Forms;
import com.karmayogi.form.repository.FormRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author anil
 */
@Component
@RequiredArgsConstructor
public class SurveyMetricsService {

    private static final Logger log = LoggerFactory.getLogger(SurveyMetricsService.class);

    private final FormRepository formRepository;
    private final ObjectMapper objectMapper;
    private final CassandraOperation cassandraOperation;

    public void processFormSubmission(String formId) {
        try {
            Forms form = formRepository.findById(formId).orElse(null);
            if (form == null) {
                log.warn("processFormSubmission form not found formId={}", formId);
                return;
            }
            Map<String, Object> additionalProps = getAdditionalProperties(form);
            long currentCount = ((Number) additionalProps.getOrDefault("submissionCount", 0)).longValue();
            additionalProps.put("submissionCount", currentCount + 1);
            updateSubmissionRate(additionalProps);
            form.setAdditionalProperties(additionalProps);
            formRepository.save(form);
            log.info("processFormSubmission updated formId={} submissionCount={}",
                    formId, currentCount + 1);
        } catch (Exception e) {
            log.error("processFormSubmission error formId={}: {}", formId, e.getMessage(), e);
        }
    }

    public void processNotificationRead(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            String formId = event.get("formId").toString();
            String userId = event.get("userId").toString();

            if (StringUtils.isBlank(formId) || StringUtils.isBlank(userId)) {
                log.warn("Invalid notification read event formId={} userId={}", formId, userId);
                return;
            }

            // dedup check via Cassandra
            Map<String, Object> queryParams = new HashMap<>();
            queryParams.put("formid", formId);
            queryParams.put("userid", userId);
            List<Map<String, Object>> records =
                    cassandraOperation.getRecordsByPropertiesByKey(
                            Constants.KEYSPACE_SUNBIRD,
                            Constants.PEER_SURVEY_NOTIFICATION_READ_TABLE,
                            queryParams, null, null);

            if (CollectionUtils.isNotEmpty(records)) {
                log.info("Notification read already processed formId={} userId={}",
                        formId, userId);
                return;
            }

            Map<String, Object> insertRecord = new HashMap<>();
            insertRecord.put("formid", formId);
            insertRecord.put("userid", userId);
            insertRecord.put("isread", true);
            insertRecord.put("readat",
                    LocalDateTime.now().atZone(ZoneId.of("UTC")).toInstant());
            cassandraOperation.insertRecord(
                    Constants.KEYSPACE_SUNBIRD,
                    Constants.PEER_SURVEY_NOTIFICATION_READ_TABLE,
                    insertRecord);

            incrementNotificationReadCount(formId);

        } catch (Exception e) {
            log.error("processNotificationRead error: {}", e.getMessage(), e);
        }
    }

    public void incrementTotalNotificationCount(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            String formId = event.get("formId").toString();
            Integer incrementBy = (Integer) event.get("incrementBy");

            if (StringUtils.isBlank(formId) || incrementBy == null) {
                log.warn("Invalid event formId={} incrementBy={}", formId, incrementBy);
                return;
            }

            Forms form = formRepository.findById(formId).orElse(null);
            if (form == null) return;

            Map<String, Object> additionalProps = getAdditionalProperties(form);
            long currentCount = ((Number) additionalProps.getOrDefault("totalNotificationCount", 0)).longValue();
            additionalProps.put("totalNotificationCount", currentCount + incrementBy);
            updateSubmissionRate(additionalProps);
            form.setAdditionalProperties(additionalProps);
            formRepository.save(form);

        } catch (Exception e) {
            log.error("incrementTotalNotificationCount error: {}", e.getMessage(), e);
        }
    }


    private void incrementNotificationReadCount(String formId) {
        try {
            Forms form = formRepository.findById(formId).orElse(null);
            if (form == null) return;
            Map<String, Object> additionalProps = getAdditionalProperties(form);
            long currentCount = ((Number) additionalProps
                    .getOrDefault("notificationReadCount", 0)).longValue();
            additionalProps.put("notificationReadCount", currentCount + 1);
            updateSubmissionRate(additionalProps);
            form.setAdditionalProperties(additionalProps);
            formRepository.save(form);
        } catch (Exception e) {
            log.error("incrementNotificationReadCount error formId={}: {}", formId, e.getMessage());
        }
    }

    private void updateSubmissionRate(Map<String, Object> additionalProps) {
        long notificationReadCount = ((Number) additionalProps
                .getOrDefault("notificationReadCount", 0)).longValue();
        long submissionCount = ((Number) additionalProps
                .getOrDefault("submissionCount", 0)).longValue();
        if (notificationReadCount <= 0) {
            additionalProps.put("submissionRate", 0L);
            return;
        }
        long rate = Math.min((submissionCount * 100) / notificationReadCount, 100);
        additionalProps.put("submissionRate", rate);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getAdditionalProperties(Forms form) {
        Map<String, Object> props = form.getAdditionalProperties();
        return props != null ? new HashMap<>(props) : new HashMap<>();
    }


}
