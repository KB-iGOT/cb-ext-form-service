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
        log.error("Error in form search", e);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("status", "failed");
        params.put("err",    "SERVER_ERROR");
        params.put("errMsg", e.getMessage());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id",           "api.forms.search");
        response.put("ver",          "v2");
        response.put("ts",           Instant.now().toString());
        response.put("params",       params);
        response.put("responseCode", "SERVER_ERROR");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
