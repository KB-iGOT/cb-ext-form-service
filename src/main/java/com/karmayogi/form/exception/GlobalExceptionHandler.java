package com.karmayogi.form.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;
/**
 * @author anil
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse(
                        "SERVER_ERROR",
                        "Something went wrong. Please try again later."));
    }

    private Map<String, Object> buildErrorResponse(String code, String message) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("status", "failed");
        params.put("err",    code);
        params.put("errMsg", message);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id",           "api.forms.search");
        response.put("ver",          "v2");
        response.put("ts",           Instant.now().toString());
        response.put("params",       params);
        response.put("responseCode", code);

        return response;
    }
}
