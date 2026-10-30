package com.karmayogi.form.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Response bodies of the old form-service v1 APIs (ResponseGenerator), kept for v1 saveFormSubmit callers.
 *
 * @author anil
 */
public final class LegacyResponses {

    private LegacyResponses() {
    }

    // {"statusInfo":{"statusCode":200,"statusMessage":"success","errorMessage":""},"responseData":<data>}
    public static Map<String, Object> success(Object responseData) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(Constants.LEGACY_STATUS_INFO,
                status(Constants.LEGACY_SUCCESS_CODE, Constants.STATUS_SUCCESS_LOWERCASE, ""));
        result.put(Constants.RESPONSE_DATA, responseData);
        return result;
    }

    // {"statusInfo":{"statusCode":320,"statusMessage":"Process failed, Please try again.","errorMessage":<message>}}
    public static Map<String, Object> failure(String errorMessage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(Constants.LEGACY_STATUS_INFO, status(Constants.LEGACY_FAILURE_CODE,
                Constants.LEGACY_PROCESS_FAILED, errorMessage == null ? "" : errorMessage));
        return result;
    }

    // save failure, which the old service returned without the statusInfo wrapper
    public static Map<String, Object> processFailed() {
        return status(Constants.LEGACY_FAILURE_CODE, Constants.LEGACY_PROCESS_FAILED, Constants.LEGACY_PROCESS_FAILED);
    }

    private static Map<String, Object> status(int code, String message, String errorMessage) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put(Constants.LEGACY_STATUS_CODE, code);
        status.put(Constants.LEGACY_STATUS_MESSAGE, message);
        status.put(Constants.LEGACY_ERROR_MESSAGE, errorMessage);
        return status;
    }
}
