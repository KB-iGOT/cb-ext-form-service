package com.karmayogi.form.exception;

import com.karmayogi.form.utils.Constants;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Getter
public enum ProjectResponseCode {
    unAuthorized(ResponseMessage.Key.UNAUTHORIZED_USER, ResponseMessage.Message.UNAUTHORIZED_USER),
    internalError(ResponseMessage.Key.INTERNAL_ERROR, ResponseMessage.Message.INTERNAL_ERROR),
    resourceNotFound(
            ResponseMessage.Key.RESOURCE_NOT_FOUND, ResponseMessage.Message.RESOURCE_NOT_FOUND),
    invalidParameterValue(
            ResponseMessage.Key.INVALID_PARAMETER_VALUE, ResponseMessage.Message.INVALID_PARAMETER_VALUE),

    OK(200),
    CLIENT_ERROR(400),
    SERVER_ERROR(500);
    @Setter
    private int responseCode;
    /**
     * error code contains String value
     */
    private String errorCode;
    /**
     * errorMessage contains proper error message.
     */
    private String errorMessage;

    /**
     * @param errorCode    String
     * @param errorMessage String
     */
    ProjectResponseCode(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    ProjectResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    /**
     * This method will provide ResponseCode enum based on error code
     */
    public static ProjectResponseCode getResponse(String errorCode) {
        if (StringUtils.isBlank(errorCode)) {
            return null;
        } else if (Constants.UNAUTHORIZED.equals(errorCode)) {
            return ProjectResponseCode.unAuthorized;
        } else {
            ProjectResponseCode value = null;
            ProjectResponseCode[] responseCodes = ProjectResponseCode.values();
            for (ProjectResponseCode response : responseCodes) {
                if (response.getErrorCode() != null && response.getErrorCode().equals(errorCode)) {
                    return response;
                }
            }
            return value;
        }
    }

    public String getMessage(int errorCode) {
        return "";
    }
}
