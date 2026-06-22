package com.karmayogi.form.service;

import com.karmayogi.form.model.FormRequest;
import com.karmayogi.form.model.FormSubmissionRequest;
import com.karmayogi.form.model.SearchCriteria;
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
}
