package com.karmayogi.form.exception;

public interface ResponseMessage {
    interface Message {

        String UNAUTHORIZED_USER = "You are not authorized.";
        String INTERNAL_ERROR = "Process failed,please try again later.";
        String RESOURCE_NOT_FOUND = "Requested {0} resource not found";
        String INVALID_PARAMETER_VALUE =
                "Invalid value {0} for parameter {1}. Please provide a valid value.";
    }

    interface Key {
        String UNAUTHORIZED_USER = "UNAUTHORIZED_USER";
        String INTERNAL_ERROR = "INTERNAL_ERROR";
        String RESOURCE_NOT_FOUND = "0013";
        String INVALID_PARAMETER_VALUE = "0017";
    }
}
