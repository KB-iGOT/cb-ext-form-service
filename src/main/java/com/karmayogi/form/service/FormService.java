package com.karmayogi.form.service;

import com.karmayogi.form.utils.ApiResponse;

import java.util.Map;

public interface FormService {
        Map<String, Object> searchForms(Map<String, Object> request);
        ApiResponse getFormById(String formId);

}
