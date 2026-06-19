package com.karmayogi.form.exception;

import com.karmayogi.form.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException e) {

        String cause = e.getMostSpecificCause().getMessage();

        if (cause != null && cause.contains("idx_assignment_unique")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(buildErrorResponse("api.forms.create.v3",
                            "DUPLICATE_ASSIGNMENT_BATCH",
                            Constants.ERR_DUPLICATE_ASSIGNMENT_BATCH));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse("api.forms.create.v3",
                        "DATA_INTEGRITY_VIOLATION",
                        "Database constraint violation"));
    }


    private Map<String, Object> buildErrorResponse( String apiId, String code, String message) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("status", "failed");
        params.put("err",    code);
        params.put("errMsg", message);

        Map<String, Object> response = new LinkedHashMap<>();
        if (apiId != null) {
            response.put("id", apiId);
        }
        response.put("ver",          "v3");
        response.put("ts",           Instant.now().toString());
        response.put("params",       params);
        response.put("responseCode", code);

        return response;
    }

    private Map<String, Object> buildErrorResponse(
            String code,
            String message) {

        return buildErrorResponse(null, code, message);
    }

}
