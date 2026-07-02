package com.karmayogi.form.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.karmayogi.form.entity.Forms;
import com.karmayogi.form.model.FieldMeta;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author anil
 */
@Component
public class FormEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FormEventPublisher.class);

    private static final java.util.Set<String> NON_QUESTION_FIELD_TYPES =
            java.util.Set.of("heading", "separator", "section");

    private final KafkaTemplate<String, String> formKafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String formEventsTopic;

    public FormEventPublisher(KafkaTemplate<String, String> formKafkaTemplate,
                              ObjectMapper objectMapper,
                              @Value("${form.kafka.topic.form-events}") String formEventsTopic) {
        this.formKafkaTemplate = formKafkaTemplate;
        this.objectMapper = objectMapper;
        this.formEventsTopic = formEventsTopic;
    }

    public void publishFormCreated(Forms form, List<FieldMeta> fields) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();

            envelope.set("formDoc", buildFormDoc(form));

            if (CollectionUtils.isNotEmpty(fields)) {
                com.fasterxml.jackson.databind.node.ArrayNode fieldDocs =
                        objectMapper.createArrayNode();
                for (FieldMeta field : fields) {
                    ObjectNode wrapper = objectMapper.createObjectNode();
                    if (field.getId() != null) {
                        wrapper.put("fieldId", field.getId());
                    }
                    wrapper.set("doc", buildFieldDoc(form.getFormId(), field));
                    fieldDocs.add(wrapper);
                }
                envelope.set("fieldDocs", fieldDocs);
            }

            String json = objectMapper.writeValueAsString(envelope);
            formKafkaTemplate.send(formEventsTopic, form.getFormId(), json);
            log.info("FormEventPublisher published formId={} fields={} topic={}",
                    form.getFormId(),
                    CollectionUtils.isEmpty(fields) ? 0 : fields.size(),
                    formEventsTopic);

        } catch (Exception e) {
            log.error("FormEventPublisher failed formId={}: {}",
                    form.getFormId(), e.getMessage(), e);
        }
    }

    private ObjectNode buildFormDoc(Forms form) {
        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("formId",      form.getFormId());
        doc.put("contextType", form.getContextType());
        doc.put("title",       form.getTitle());
        doc.put("status",      form.getStatus());
        doc.put("createdBy",   form.getCreatedBy());
        doc.put("updatedBy",   form.getUpdatedBy());

        if (form.getVersion() != null)
            doc.put("version", form.getVersion());
        if (form.getClientVersion() != null)
            doc.put("clientVersion", form.getClientVersion());
        if (form.getCreatedAt() != null)
            doc.put("createdDate", form.getCreatedAt());
        if (form.getUpdatedAt() != null)
            doc.put("updatedDate", form.getUpdatedAt());
        if (form.getStartDate() != null)
            doc.put("startDate", form.getStartDate());
        if (form.getEndDate() != null)
            doc.put("endDate", form.getEndDate());
        if (form.getArchivedDate() != null)
            doc.put("archivedDate", form.getArchivedDate());

        if (form.getOrgId() != null || form.getOrgName() != null) {
            com.fasterxml.jackson.databind.node.ArrayNode createdFor =
                    objectMapper.createArrayNode();
            ObjectNode org = objectMapper.createObjectNode();
            if (form.getOrgId() != null)   org.put("orgId",   form.getOrgId());
            if (form.getOrgName() != null) org.put("orgName", form.getOrgName());
            createdFor.add(org);
            doc.set("createdFor", createdFor);
        }

        if (form.getAdditionalProperties() != null)
            doc.set("additionalProperties",
                    objectMapper.valueToTree(form.getAdditionalProperties()));
        if (form.getMandatoryFields() != null)
            doc.set("mandatoryFields",
                    objectMapper.valueToTree(form.getMandatoryFields()));
        if (form.getMeta() != null)
            doc.set("meta", objectMapper.valueToTree(form.getMeta()));

        return doc;
    }

    private ObjectNode buildFieldDoc(String formId, FieldMeta field) {
        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("formId",           formId);
        doc.put("contextType",      getFieldContextType(field.getFieldType()));
        doc.put("name",             field.getName());
        doc.put("fieldType",        field.getFieldType());
        doc.put("order",            field.getOrder() != null ? field.getOrder() : 0);
        doc.put("isRequired",       Boolean.TRUE.equals(field.getIsRequired()));
        doc.put("notApplicable",    Boolean.TRUE.equals(field.getNotApplicable()));
        doc.put("hidden",           Boolean.TRUE.equals(field.getHidden()));
        doc.put("refApi",           field.getRefApi() != null ? field.getRefApi() : "");
        doc.put("logicalGroupCode", field.getLogicalGroupCode() != null
                ? field.getLogicalGroupCode() : "");

        if (field.getParentId() != null)
            doc.put("parentId", field.getParentId());
        if (field.getSectionId() != null)
            doc.put("sectionId", field.getSectionId());
        if (field.getValues() != null)
            doc.set("values", objectMapper.valueToTree(field.getValues()));
        if (field.getAdditionalProperties() != null)
            doc.set("additionalProperties",
                    objectMapper.valueToTree(field.getAdditionalProperties()));

        return doc;
    }

    private String getFieldContextType(String fieldType) {
        if (fieldType == null) return "question";
        return NON_QUESTION_FIELD_TYPES.contains(fieldType.toLowerCase())
                ? fieldType.toLowerCase()
                : "question";
    }

    public void publishFormUpdated(Forms form, List<FieldMeta> fields,
                                   java.util.Set<String> staleFieldIds) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventType", "UPDATE");
            envelope.set("formDoc", buildFormDoc(form));


            if (CollectionUtils.isNotEmpty(fields)) {
                com.fasterxml.jackson.databind.node.ArrayNode fieldDocs =
                        objectMapper.createArrayNode();
                for (FieldMeta field : fields) {
                    ObjectNode wrapper = objectMapper.createObjectNode();
                    if (field.getId() != null) {
                        wrapper.put("fieldId", field.getId());
                    }
                    wrapper.set("doc", buildFieldDoc(form.getFormId(), field));
                    fieldDocs.add(wrapper);
                }
                envelope.set("fieldDocs", fieldDocs);
            }

            if (staleFieldIds != null && !staleFieldIds.isEmpty()) {
                com.fasterxml.jackson.databind.node.ArrayNode staleIds =
                        objectMapper.createArrayNode();
                staleFieldIds.forEach(staleIds::add);
                envelope.set("staleFieldIds", staleIds);
            }

            String json = objectMapper.writeValueAsString(envelope);
            formKafkaTemplate.send(formEventsTopic, form.getFormId(), json);
            log.info("FormEventPublisher published UPDATE formId={} fields={} stale={} topic={}",
                    form.getFormId(),
                    CollectionUtils.isEmpty(fields) ? 0 : fields.size(),
                    staleFieldIds == null ? 0 : staleFieldIds.size(),
                    formEventsTopic);
        } catch (Exception e) {
            log.error("FormEventPublisher publishFormUpdated failed formId={}: {}",
                    form.getFormId(), e.getMessage(), e);
        }
    }

    public void pushToPipeline(Object payload, String topic, String key) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            formKafkaTemplate.send(topic, key, json);
            log.info("pushToPipeline sent topic={} key={}", topic, key);
        } catch (Exception e) {
            log.error("pushToPipeline failed topic={} key={}: {}", topic, key, e.getMessage(), e);
        }
    }

}
