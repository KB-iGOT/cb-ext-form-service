package com.karmayogi.form.exception;

import org.springframework.http.HttpStatus;

/**
 * @author anil
 */

public class CustomException extends RuntimeException {
    private String code;
    private String message;
    private HttpStatus httpStatusCode;
    private String errorCode;
    private int responseCode;

    public CustomException() {
    }

    public CustomException(String code, String message, HttpStatus httpStatusCode) {
        this.code = code;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
    }


    public CustomException(String code, String message, int responseCode) {
        this.code = code;
        this.message = message;
        this.responseCode = responseCode;
    }

    public CustomException(String code, String message, HttpStatus httpStatusCode, String errorCode) {
        this.code = code;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
        this.errorCode = errorCode;
    }
}
