package com.karmayogi.form.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmayogi.form.entity.FormQuestions;
import com.karmayogi.form.entity.Forms;
import com.karmayogi.form.model.FieldMeta;
import com.karmayogi.form.model.FormRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
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
public class FormEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(FormEntityMapper.class);


    private final ObjectMapper objectMapper;

    /**
     * Maps FormRequest to Forms JPA entity
     *
     * @param request    incoming form request
     * @param formId     generated formId (epoch millis)
     * @param userId     authenticated user
     * @param contextType validated contextType
     * @return Forms entity ready for INSERT
     */
    public Forms toFormsEntity(FormRequest request, String formId,
                               String userId, String contextType) {

        long now = System.currentTimeMillis();

        Forms form = new Forms();
        form.setFormId(formId);
        form.setTitle(request.getTitle());
        form.setStatus(request.getStatus());
        form.setContextType(contextType);
        form.setVersion(1);
        form.setClientVersion(request.getClientVersion() != null
                ? request.getClientVersion().doubleValue() : null);
        form.setCreatedAt(now);
        form.setUpdatedAt(now);
        form.setCreatedBy(userId);
        form.setUpdatedBy(userId);

        if (CollectionUtils.isNotEmpty(request.getCreatedFor())) {
            form.setOrgId(request.getCreatedFor().get(0).getOrgId());
            form.setOrgName(request.getCreatedFor().get(0).getOrgName());
        }

        if (MapUtils.isNotEmpty(request.getAdditionalProperties())) {
            form.setAdditionalProperties(request.getAdditionalProperties());
            extractBatchAndCourse(request.getAdditionalProperties(), form);
        }

        if (CollectionUtils.isNotEmpty(request.getMeta())) {
            form.setMeta(request.getMeta());
        }

        return form;
    }

    /**
     * Maps list of FieldMeta to list of FormQuestions JPA entities
     *
     * @param fields  incoming fields from request
     * @param formId  parent formId
     * @param status  form status
     * @return List of FormQuestions entities ready for batch INSERT
     */
    public List<FormQuestions> toFormQuestionsEntities(List<FieldMeta> fields,
                                                       String formId,
                                                       String status) {
        if (CollectionUtils.isEmpty(fields)) {
            return Collections.emptyList();
        }

        List<FormQuestions> questions = new ArrayList<>(fields.size());
        for (FieldMeta field : fields) {
            questions.add(toFormQuestionsEntity(field, formId, status));
        }
        return questions;
    }

    /**
     * Maps single FieldMeta to FormQuestions JPA entity
     */
    public FormQuestions toFormQuestionsEntity(FieldMeta field,
                                               String formId,
                                               String status) {
        FormQuestions q = new FormQuestions();
        q.setQuestionId(StringUtils.isNotBlank(field.getId())
                ? field.getId()
                : UUID.randomUUID().toString());
        q.setFormId(formId);
        q.setName(field.getName());
        q.setFieldType(field.getFieldType());
        q.setQuestionOrder(field.getOrder());
        q.setIsRequired(field.getIsRequired());
        q.setNotApplicable(field.getNotApplicable());
        q.setStatus(status);
        q.setSectionId(field.getSectionId());
        q.setParentId(field.getParentId());
        q.setHidden(field.getHidden());
        q.setLogicalGroupCode(field.getLogicalGroupCode());
        q.setRefApi(field.getRefApi());
        q.setValues(objectMapper.convertValue(
                field.getValues(),
                new TypeReference<List<Map<String, Object>>>() {}));
        q.setAdditionalProperties(
                MapUtils.isNotEmpty(field.getAdditionalProperties())
                        ? field.getAdditionalProperties() : null);
        return q;
    }

    /**
     * Adds mandatory "Course ID and Name" hidden field to form
     * Called for all contextTypes except assignment
     */
    public void addMandatoryFields(Forms form) {
        String json = "[{\"refApi\":\"\",\"logicalGroupCode\":\"\","
                + "\"name\":\"Course ID and Name\",\"fieldType\":\"text\","
                + "\"values\":[],\"isRequired\":true,\"order\":99,"
                + "\"additionalProperties\":{},\"hidden\":true}]";
        try {
            form.setMandatoryFields(objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {}));
        } catch (Exception e) {
            log.error("addMandatoryFields error: {}", e.getMessage());
        }
    }

    /**
     * Sets form status based on contextType and request status
     * peerValidationSurvey → always Draft
     * Others → PUBLISH / Draft / Unpublish
     */
    public void setFormStatus(FormRequest request) {
        if (Constants.PEER_VALIDATION_SURVEY
                .equalsIgnoreCase(request.getContextType())) {
            request.setStatus("Draft");
            return;
        }
        if (StringUtils.isNotBlank(request.getStatus())
                && request.getStatus().equalsIgnoreCase("DRAFT")) {
            request.setStatus("Draft");
        } else if (StringUtils.isNotBlank(request.getStatus())
                && request.getStatus().equalsIgnoreCase("UNPUBLISH")) {
            request.setStatus("Unpublish");
        } else {
            request.setStatus("PUBLISH");
        }
    }

    private void extractBatchAndCourse(Map<String, Object> additionalProperties,
                                       Forms form) {
        Object batchId = additionalProperties.get("batchId");
        if (batchId != null) {
            form.setBatchId(batchId.toString());
        }

        Object courseId = additionalProperties.getOrDefault("courseId",
                additionalProperties.get("identifier"));
        if (courseId != null) {
            form.setCourseId(courseId.toString());
        }
    }


    public Long parseEndDate(String endDate) {
        if (org.apache.commons.lang3.StringUtils.isBlank(endDate)) return null;
        try {
            return Long.parseLong(endDate);
        } catch (NumberFormatException e) {
            try {
                java.time.LocalDate date = java.time.LocalDate.parse(endDate);
                return date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public void populateFieldIds(List<FieldMeta> fields) {
        if (CollectionUtils.isEmpty(fields)) {
            return;
        }
        fields.forEach(f -> {
            if (StringUtils.isBlank(f.getId())) {
                f.setId(UUID.randomUUID().toString());
            }
        });
    }

    public void applyUpdateFields(Forms form, FormRequest request, String userId) {
        long now = System.currentTimeMillis();
        if (StringUtils.isNotBlank(request.getTitle()))
            form.setTitle(request.getTitle());
        if (request.getMeta() != null)
            form.setMeta(request.getMeta());
        if (CollectionUtils.isNotEmpty(request.getMandatoryFields()))
            form.setMandatoryFields(request.getMandatoryFields());
        if (MapUtils.isNotEmpty(request.getAdditionalProperties()))
            form.setAdditionalProperties(request.getAdditionalProperties());
        if (StringUtils.isNotBlank(request.getEndDate()))
            form.setEndDate(parseEndDate(request.getEndDate()));
        if (request.getClientVersion() != null)
            form.setClientVersion(request.getClientVersion().doubleValue());
        if (StringUtils.isNotBlank(request.getStatus()))
            form.setStatus(request.getStatus());
        form.setUpdatedBy(userId);
        form.setUpdatedAt(now);
    }

}
