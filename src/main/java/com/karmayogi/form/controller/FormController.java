package com.karmayogi.form.controller;

import com.karmayogi.form.model.FormRequest;
import com.karmayogi.form.model.SearchCriteria;
import com.karmayogi.form.service.FormService;
import com.karmayogi.form.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * @author anil
 */
@RestController
@RequestMapping("/forms")
@RequiredArgsConstructor
public class FormController {

    private final FormService formService;

    @GetMapping(value = "/read", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse> getFormById(@RequestParam("formId") String formId) {
        ApiResponse response = formService.getFormById(formId);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse> createForm(
            @RequestBody FormRequest request, @RequestHeader("x-authenticated-userid") String userId) {
        return ResponseEntity.ok(formService.createForm(request, userId));
    }

    @PostMapping(value = "/update", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse> updateForm(
            @RequestBody FormRequest request, @RequestHeader("x-authenticated-userid") String userId) {
        return ResponseEntity.ok(formService.updateForm(request, userId));
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse> searchForms(@RequestBody SearchCriteria criteria) {
        return ResponseEntity.ok(formService.searchForms(criteria));
    }

}
