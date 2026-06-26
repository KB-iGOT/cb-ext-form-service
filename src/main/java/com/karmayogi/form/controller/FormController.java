package com.karmayogi.form.controller;

import com.karmayogi.form.model.*;
import com.karmayogi.form.service.FormService;
import com.karmayogi.form.utils.ApiResponse;
import com.karmayogi.form.utils.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.karmayogi.form.utils.Constants.X_AUTHENTICATED_USER_ID;
import static com.karmayogi.form.utils.Constants.X_AUTH_TOKEN;

/**
 * @author anil
 */
@RestController
@RequestMapping("/forms/v3")
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
            @RequestBody FormRequest request, @RequestHeader(X_AUTHENTICATED_USER_ID) String userId) {
        return ResponseEntity.ok(formService.createForm(request, userId));
    }

    @PostMapping(value = "/update", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse> updateForm(
            @RequestBody FormRequest request, @RequestHeader(X_AUTHENTICATED_USER_ID) String userId) {
        return ResponseEntity.ok(formService.updateForm(request, userId));
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse> searchForms(@RequestBody SearchCriteria criteria) {
        return ResponseEntity.ok(formService.searchForms(criteria));
    }

    @PostMapping("/saveFormSubmit")
    public ResponseEntity<ApiResponse> submitForm(
            @RequestBody FormSubmissionRequest request,
            @RequestHeader(value = X_AUTHENTICATED_USER_ID, required = false) String userId) {
        return ResponseEntity.ok(formService.submitForm(request, userId, false));
    }

    @GetMapping("/getApplicationsById")
    public ResponseEntity<ApiResponse> getUserSavedForm(
            @RequestParam String formId,
            @RequestParam(required = false) String status,
            @RequestParam String contextId,
            @RequestHeader(value = X_AUTHENTICATED_USER_ID) String userId) {
        return ResponseEntity.ok(formService.getUserSavedForm(formId, status, userId, contextId));
    }

    @GetMapping(value = "/getAllApplications")
    public ResponseEntity<ApiResponse> getAllSubmittedApplications(
            @RequestParam(value = Constants.FORM_ID) String formId) {
        return ResponseEntity.ok(formService.getAllApplications(formId));
    }

    @PostMapping("/submission/search")
    public ResponseEntity<ApiResponse> searchUserFeedbackForms(@RequestBody SearchCriteria criteria) {
        return ResponseEntity.ok(formService.searchUserFeedbackForms(criteria));
    }

    @PostMapping("/feedback")
    public ResponseEntity<ApiResponse> saveFeedback(@RequestHeader(value = X_AUTHENTICATED_USER_ID) String userId, @RequestBody FeedbackRequest request) {
        return ResponseEntity.ok(formService.saveFeedback(request, userId));
    }

    @PutMapping("/submitDraft")
    public ResponseEntity<ApiResponse> saveOrUpdateAnswer(@RequestHeader(value = X_AUTHENTICATED_USER_ID) String userId, @RequestBody FormSubmissionRequest request) {
        return ResponseEntity.ok(formService.processAssignmentAnswer(request, userId, true));
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse> submitAnswer(@RequestHeader(value = X_AUTHENTICATED_USER_ID) String userId, @RequestBody FormSubmissionRequest request) {
        return ResponseEntity.ok(formService.processAssignmentAnswer(request, userId, false));
    }

    @PostMapping("/bulkGetApplicationsById")
    public ResponseEntity<ApiResponse> getUserApplicationsBulk(@RequestBody Map<String, Object> requestBody) {
        return ResponseEntity.ok(formService.getUserSavedFormsBulk(requestBody));
    }

    @PostMapping("/public/saveFormSubmit")
    public ResponseEntity<ApiResponse> publicSubmitForm(@RequestBody PublicFormSubmissionRequest request) {
        return ResponseEntity.ok(formService.publicSubmitForm(request));
    }

    @PostMapping("/mdo/peersurvey/create")
    public ResponseEntity<Object> createPeerEvaluationSurveyForMDO(
            @RequestBody FormRequest form,
            @RequestHeader(X_AUTH_TOKEN) String token) {

        ApiResponse response = formService.createPeerEvaluationSurveyForMDO(form, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/spv/peersurvey/create")
    public ResponseEntity<ApiResponse> createPeerEvaluationSurveyForSPV(
            @RequestBody FormRequest form,
            @RequestHeader(X_AUTH_TOKEN) String token) {
        return ResponseEntity.ok(formService.createPeerEvaluationSurveyForSPV(form, token));
    }

    @PutMapping("/mdo/peersurvey/{surveyId}")
    public ResponseEntity<ApiResponse> updateMdoPeerSurvey(@PathVariable String surveyId, @RequestBody FormRequest request, @RequestHeader(X_AUTH_TOKEN) String token) {
        request.setFormId(surveyId);
        return ResponseEntity.ok(formService.updatePeerSurvey(request, token));
    }

    @PutMapping("/spv/peersurvey/{surveyId}")
    public ResponseEntity<ApiResponse> updateSpvPeerSurvey(@PathVariable String surveyId, @RequestBody FormRequest request, @RequestHeader(X_AUTH_TOKEN) String token) {
        request.setFormId(surveyId);
        return ResponseEntity.ok(formService.updatePeerSurvey(request, token));
    }

    @PostMapping("/mdo/peersurvey/search")
    public ApiResponse searchMdoPeerSurveys(
            @RequestBody SearchCriteria criteria, @RequestHeader(X_AUTH_TOKEN) String token) {
        return formService.searchPeerSurvey(criteria, token, false);
    }

    @PostMapping("/spv/peersurvey/search")
    public ApiResponse searchSpvPeerSurveys(
            @RequestBody SearchCriteria criteria, @RequestHeader(X_AUTH_TOKEN) String token) {

        return formService.searchPeerSurvey(criteria, token, true);
    }

    @PutMapping("/peersurvey/publish/{surveyId}")
    public ResponseEntity<ApiResponse> publishPeerSurvey(@PathVariable String surveyId, @RequestHeader(X_AUTH_TOKEN) String token) {
        return ResponseEntity.ok(formService.publishPeerSurvey(surveyId, token));
    }

    @PutMapping("/peersurvey/end/{surveyId}")
    public ResponseEntity<ApiResponse> endPeerSurvey(@PathVariable String surveyId, @RequestHeader(X_AUTH_TOKEN) String token) {
        return ResponseEntity.ok(formService.endPeerSurvey(surveyId, token));
    }

    @PutMapping("/peersurvey/archive/{surveyId}")
    public ResponseEntity<ApiResponse> archivePeerSurvey(@PathVariable String surveyId, @RequestHeader(X_AUTH_TOKEN) String token) {
        return ResponseEntity.ok(formService.archivePeerSurvey(surveyId, token));
    }

    @PostMapping("/peersurvey/submit")
    public ResponseEntity<ApiResponse> submitPeerValidationSurvey(@RequestHeader(X_AUTH_TOKEN) String token, @RequestBody FormSubmissionRequest request) {
        return ResponseEntity.ok(formService.submitPeerValidationSurvey(request, token, false));
    }
}
