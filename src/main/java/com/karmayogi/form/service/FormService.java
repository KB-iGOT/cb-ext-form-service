package com.karmayogi.form.service;

import com.karmayogi.form.model.*;
import com.karmayogi.form.utils.ApiResponse;

import java.util.Map;

public interface FormService {
        Map<String, Object> searchForms(Map<String, Object> request);
        ApiResponse getFormById(String formId);
        ApiResponse createForm(FormRequest request, String userId);
        ApiResponse updateForm(FormRequest request, String userId);
        ApiResponse searchForms(SearchCriteria criteria);
        ApiResponse submitForm(FormSubmissionRequest request, String userId, boolean isAnonymousUser);
        ApiResponse getUserSavedForm(String formId, String status, String userId, String contextId);
        ApiResponse getAllApplications(String formId);
        ApiResponse searchUserFeedbackForms(SearchCriteria criteria);
        ApiResponse saveFeedback(FeedbackRequest request, String userId);
        ApiResponse processAssignmentAnswer(FormSubmissionRequest request, String userId, boolean isDraft);
        ApiResponse getUserSavedFormsBulk(Map<String, Object> requestBody);
        ApiResponse publicSubmitForm(PublicFormSubmissionRequest request);
        ApiResponse createPeerEvaluationSurveyForMDO(FormRequest form, String token);
        ApiResponse createPeerEvaluationSurveyForSPV(FormRequest form, String token);
        ApiResponse updatePeerSurvey(FormRequest request, String token);
        ApiResponse searchPeerSurvey(SearchCriteria criteria, String token, boolean isSpvSearch);
        ApiResponse publishPeerSurvey(String formId, String token);
        ApiResponse archivePeerSurvey(String formId, String token);
        ApiResponse endPeerSurvey(String formId, String token);
        ApiResponse submitPeerValidationSurvey(FormSubmissionRequest request, String token, boolean isAnonymousUser);
}
