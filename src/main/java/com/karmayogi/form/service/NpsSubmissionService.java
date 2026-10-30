package com.karmayogi.form.service;

import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.utils.Constants;
import com.karmayogi.form.utils.FormEventPublisher;
import com.karmayogi.form.utils.LegacyResponses;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NPS (platform rating) submissions, as the old form-service saved them: one doc per rating in
 * the v1 data index, an activity-log entry, and a completion event on the NPS Kafka topics.
 *
 * @author anil
 */
@Service
@RequiredArgsConstructor
public class NpsSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(NpsSubmissionService.class);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestHighLevelClient client;
    private final FormEventPublisher formEventPublisher;
    private final FormConfig formConfig;

    public boolean isNpsForm(String formId) {
        return isPrimary(formId) || isUpgraded(formId);
    }

    public ResponseEntity<Object> submit(Map<String, Object> body, String fullUserId) {
        Long formId = Long.valueOf(String.valueOf(body.get(Constants.FORM_ID)).trim());
        String userId = resolveUserId(fullUserId);
        Map<String, Object> dataObject = body.get(Constants.DATA_OBJECT) instanceof Map<?, ?> m
                ? removeEmptyFields(m) : null;

        // popup closed with nothing filled in: nothing is saved, only the "not submitted" event
        if (MapUtils.isEmpty(dataObject)) {
            Long timestamp = checkTimestamp(toLong(body.get(Constants.TIMESTAMP), System.currentTimeMillis()));
            publish(formId, userId, null, timestamp, false, false);
            return ResponseEntity.ok(LegacyResponses.success(Constants.NPS_DATA_OBJECT_MISSING));
        }

        Map<String, Object> doc = new LinkedHashMap<>();
        String applicationId = (String) body.get(Constants.APPLICATION_ID);
        boolean isNew = StringUtils.isBlank(applicationId);
        if (isNew) {
            applicationId = RandomStringUtils.random(15, true, true);
        }
        long rawTimestamp = toLong(body.get(Constants.TIMESTAMP), System.currentTimeMillis());
        doc.put(Constants.FORM_ID, formId);
        doc.put(Constants.APPLICATION_ID, applicationId);
        doc.put(Constants.VERSION, body.get(Constants.VERSION) instanceof Number v ? v.intValue() : 0);
        doc.put(Constants.TIMESTAMP, rawTimestamp);
        doc.put(Constants.DATA_OBJECT, dataObject);
        doc.put(Constants.STATUS, Constants.DRAFT.equalsIgnoreCase((String) body.get(Constants.STATUS))
                ? Constants.DRAFT : Constants.NPS_STATUS_NEW);
        doc.put(Constants.CREATED_BY, userId);
        if (isNew) {
            doc.put(Constants.CREATED_DATE, LocalDateTime.now().format(DATE));
        }
        doc.put(Constants.UPDATED_BY, userId);
        if (!isNew) {
            doc.put(Constants.UPDATED_DATE, LocalDateTime.now().format(DATE));
        }

        if (!save(applicationId, doc, isNew)) {
            return ResponseEntity.ok(LegacyResponses.processFailed());
        }
        if (isNew) {
            writeActivityLog(applicationId, doc);
        }

        Integer rating = rating(dataObject);
        publish(formId, userId, rating, checkTimestamp(rawTimestamp), rating != null, true);
        return ResponseEntity.ok(LegacyResponses.success(Boolean.TRUE));
    }

    private boolean save(String applicationId, Map<String, Object> doc, boolean isNew) {
        try {
            if (isNew) {
                client.index(new IndexRequest(formConfig.getNpsDataIndex(), Constants.DOC_TYPE, applicationId)
                        .source(doc), RequestOptions.DEFAULT);
            } else {
                client.update(new UpdateRequest(formConfig.getNpsDataIndex(), Constants.DOC_TYPE, applicationId)
                        .doc(doc), RequestOptions.DEFAULT);
            }
            log.info("NPS submission saved applicationId={} formId={}", applicationId, doc.get(Constants.FORM_ID));
            return true;
        } catch (Exception e) {
            log.error("NPS submission save failed applicationId={}: {}", applicationId, e.getMessage(), e);
            return false;
        }
    }

    private void writeActivityLog(String applicationId, Map<String, Object> doc) {
        try {
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put(Constants.DATA_OBJECT, change(Constants.DATA_OBJECT, doc.get(Constants.DATA_OBJECT)));
            changes.put(Constants.STATUS, change(Constants.STATUS, doc.get(Constants.STATUS)));

            Map<String, Object> activity = new LinkedHashMap<>();
            activity.put(Constants.ID, applicationId);
            activity.put(Constants.TYPE, Constants.NPS_ACTIVITY_TYPE);
            activity.put(Constants.UPDATED_DATE, LocalDateTime.now().format(DATE_TIME));
            activity.put(Constants.NPS_UPDATED_OBJECT, doc);
            activity.put(Constants.TIMESTAMP, System.currentTimeMillis());
            activity.put(Constants.NPS_CHANGES, changes);
            client.index(new IndexRequest(formConfig.getNpsActivityLogIndex(), Constants.DOC_TYPE,
                    RandomStringUtils.random(15, true, true)).source(activity), RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.error("NPS activity log failed applicationId={}: {}", applicationId, e.getMessage());
        }
    }

    private Map<String, Object> change(String field, Object value) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put(Constants.NPS_CHANGED_TO, value);
        change.put(Constants.NPS_FIELD, field);
        change.put(Constants.NPS_ACTION, Constants.NPS_ACTION_CREATED);
        return change;
    }

    /**
     * Primary/secondary NPS form: nps_submission topic, submitted = rating found.
     * Upgraded NPS form: nps_upgraded_submission topic with userType, -1 counts as not submitted.
     * A discarded popup sends submitted = false on both.
     */
    private void publish(Long formId, String userId, Integer rating, Long timestamp,
                         boolean ratingFound, boolean saved) {
        String id = String.valueOf(formId);
        if (isPrimary(id)) {
            Map<String, Object> event = event(userId, rating, timestamp, saved && ratingFound, null, formId);
            formEventPublisher.pushToPipeline(event, formConfig.getNpsTopic(), formConfig.getNpsTopic());
        }
        if (isUpgraded(id)) {
            boolean submitted = saved && ratingFound && rating != -1;
            Map<String, Object> event = event(userId, rating, timestamp, submitted, userType(saved ? rating : null), formId);
            formEventPublisher.pushToPipeline(event, formConfig.getNpsUpgradedTopic(), formConfig.getNpsUpgradedTopic());
        }
    }

    private Map<String, Object> event(String userId, Integer rating, Long timestamp, boolean submitted,
                                      String userType, Long formId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put(Constants.NPS_USER_ID, userId);
        event.put(Constants.NPS_RATING, rating);
        event.put(Constants.TIMESTAMP, timestamp);
        event.put(Constants.NPS_SUBMITTED, submitted);
        if (userType != null) {
            event.put(Constants.NPS_USER_TYPE, userType);
        }
        event.put(Constants.NPS_ACTIVITY_DESC, Constants.NPS_SUBMISSION_ACTIVITY);
        event.put(Constants.NPS_ACTIVITY_ID, formId);
        event.put(Constants.NPS_ORIGIN_PLATFORM, Constants.NPS_ORIGIN_FORMS_SERVICE);
        return event;
    }

    // old service spelling ("Detracters") is kept; downstream reports match on it
    private String userType(Integer rating) {
        if (rating == null || rating <= 6) return Constants.NPS_DETRACTORS;
        if (rating < 9) return Constants.NPS_PASSIVES;
        return Constants.NPS_PROMOTERS;
    }

    // first answer whose question mentions "rate"
    private Integer rating(Map<String, Object> dataObject) {
        for (Map.Entry<String, Object> entry : dataObject.entrySet()) {
            if (entry.getKey().toLowerCase().contains("rate")) {
                Object value = entry.getValue();
                try {
                    return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    // drops blank keys and empty-string answers, nested maps included
    private Map<String, Object> removeEmptyFields(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String name = String.valueOf(key);
            if (name.isEmpty() || "".equals(value)) {
                return;
            }
            result.put(name, value instanceof Map<?, ?> nested ? removeEmptyFields(nested) : value);
        });
        return result;
    }

    // "f:<channel>:<userId>" from the proxy header becomes "<userId>"
    private String resolveUserId(String fullUserId) {
        if (StringUtils.isNotBlank(fullUserId) && fullUserId.contains(":")) {
            String[] parts = fullUserId.split(":");
            if (parts.length > 2) {
                return parts[2];
            }
        }
        return StringUtils.defaultString(fullUserId);
    }

    // microsecond timestamps are brought down to milliseconds
    private Long checkTimestamp(long timestamp) {
        return String.valueOf(timestamp).length() > 13 ? timestamp / 1000 : timestamp;
    }

    private long toLong(Object value, long fallback) {
        if (value instanceof Number n) return n.longValue();
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean isPrimary(String formId) {
        return StringUtils.isNotBlank(formId)
                && (formId.equals(formConfig.getNpsFormId()) || formId.equals(formConfig.getNpsFormSecondaryId()));
    }

    private boolean isUpgraded(String formId) {
        return StringUtils.isNotBlank(formId) && formId.equals(formConfig.getNpsFormUpgradedId());
    }
}
