package com.karmayogi.form.utils;

import com.karmayogi.form.entity.FormQuestions;
import com.karmayogi.form.entity.Forms;
import org.apache.commons.collections4.MapUtils;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.karmayogi.form.utils.Constants.*;

/**
 * @author anil
 */

public class ResponseUtil {

    public static Map<String, Object> buildResponse(String apiId, Object result) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("status", "success");
        params.put("err", null);
        params.put("errMsg", null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id",           apiId);
        response.put("ver",          "v2");
        response.put("ts",           Instant.now().toString());
        response.put("params",       params);
        response.put("responseCode", "OK");
        response.put("result",       Map.of("response", result));

        return response;
    }

    public static Map<String, Object> buildQuestionResponse(FormQuestions q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(QUESTION_ID,                   q.getQuestionId());
        m.put(NAME,                 q.getName());
        m.put(FIELD_TYPE,            q.getFieldType());
        m.put(ORDER,                q.getQuestionOrder());
        m.put(IS_REQUIRED,           q.getIsRequired());
        m.put(NOT_APPLICABLE,        q.getNotApplicable());
        m.put(STATUS,               q.getStatus());
        m.put(SECTION_ID,            q.getSectionId());
        m.put(PARENT_ID,             q.getParentId());
        m.put(HIDDEN,               q.getHidden());
        m.put(LOGICAL_GROUP_CODE,     q.getLogicalGroupCode());
        m.put(REFAPI,               q.getRefApi());
        m.put(VALUES,               q.getValues());
        if (MapUtils.isNotEmpty(q.getAdditionalProperties())) {
            m.putAll(q.getAdditionalProperties());
        }
        m.put(CONTEXT_TYPE, QUESTION);
        m.put(FORM_ID,      q.getFormId());
        return m;
    }

    public static Map<String, Object> buildFormResponse(Forms f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(FORM_ID,               f.getFormId());
        m.put(TITLE,                f.getTitle());
        m.put(STATUS,               f.getStatus());
        m.put(CONTEXT_TYPE,          f.getContextType());
        m.put(VERSION,              f.getVersion());
        m.put(CLIENT_VERSION,        f.getClientVersion());
        m.put(CREATED_DATE,          f.getCreatedAt());
        m.put(UPDATED_DATE,          f.getUpdatedAt());
        m.put(START_DATE,            f.getStartDate());
        m.put(END_DATE,              f.getEndDate());
        m.put(ARCHIVED_DATE,         f.getArchivedDate());
        m.put(CREATED_BY,            f.getCreatedBy());
        m.put(UPDATED_BY,            f.getUpdatedBy());
        m.put(ORG_ID,                f.getOrgId());
        m.put(ORG_NAME,              f.getOrgName());
        m.put(BATCH_ID,              f.getBatchId());
        m.put(COURSE_ID,             f.getCourseId());
        m.put(ADDITIONAL_PROPERTIES, f.getAdditionalProperties());
        m.put(MANDATORY_FIELDS,      f.getMandatoryFields());
        m.put(META,                 f.getMeta());

        if (VERSION_2.equals(Optional.ofNullable(f.getClientVersion()).orElse(1.1))
                && f.getQuestions() != null) {
            m.put(FIELDS, f.getQuestions());
        }
        return m;
    }

    public static ApiResponse buildSuccess(ApiResponse response, Map<String, Object> result) {
        response.getParams().setStatus(STATUS_SUCCESS);
        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.RESPONSE, result);
        return response;
    }

    public static ApiResponse buildError(ApiResponse response,
                                   String errMsg,
                                   HttpStatus status) {
        response.getParams().setStatus(STATUS_FAILED);
        response.getParams().setErrMsg(errMsg);
        response.setResponseCode(status);
        return response;
    }

    private ResponseUtil() {
    }
}
