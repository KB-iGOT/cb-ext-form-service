package com.karmayogi.form.service;

import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.entity.FormQuestions;
import com.karmayogi.form.entity.Forms;
import com.karmayogi.form.repository.FormQuestionsRepository;
import com.karmayogi.form.repository.FormRepository;
import com.karmayogi.form.utils.ApiResponse;
import com.karmayogi.form.utils.Constants;
import com.karmayogi.form.utils.FormCacheService;
import com.karmayogi.form.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.karmayogi.form.utils.Constants.*;
import static com.karmayogi.form.utils.ResponseUtil.buildError;
import static com.karmayogi.form.utils.ResponseUtil.buildSuccess;

/**
 * @author anil
 */
@Service
@RequiredArgsConstructor
public class FormServiceImpl implements FormService{
    private static final Logger log = LoggerFactory.getLogger(FormServiceImpl.class);

    private final FormRepository formRepository;
    private final FormConfig formConfig;
    private final FormCacheService formCacheService;
    private final FormQuestionsRepository formQuestionsRepository;


    @Override
    public Map<String, Object> searchForms(Map<String, Object> request) {
        return Map.of();
    }

    public ApiResponse getFormById(String formId) {

        ApiResponse response = new ApiResponse(API_GET_FORM_BY_ID);

        if (StringUtils.isBlank(formId)) {
            log.warn(Constants.LOG_FORM_ID_BLANK);
            return buildError(response,
                    Constants.ERR_FORM_ID_REQUIRED,
                    HttpStatus.BAD_REQUEST);
        }

        try {
            Map<String, Object> cached = formCacheService.get(formId);
            if (MapUtils.isNotEmpty(cached)) {
                log.info(LOG_CACHE_HIT, formId);
                return buildSuccess(response, cached);
            }
            Optional<Forms> formOpt = formRepository.findById(formId);
            if (formOpt.isEmpty()) {
                log.warn(Constants.LOG_FORM_NOT_FOUND, formId);
                return buildError(response,
                        ERR_FORM_NOT_FOUND + formId,
                        HttpStatus.NOT_FOUND);
            }
            Forms   form = formOpt.get();
            boolean isV2 = Objects.nonNull(form.getQuestions());
            Map<String, Object> result = ResponseUtil.buildFormResponse(form);

            if (!isV2) {
                List<FormQuestions> questions = formQuestionsRepository.findByFormIdOrderByQuestionOrderAsc(formId);
                if (CollectionUtils.isNotEmpty(questions)) {
                    List<Map<String, Object>> fields = questions.stream()
                            .filter(q -> Objects.nonNull(q) && StringUtils.isNotBlank(q.getQuestionId()))
                            .map(ResponseUtil::buildQuestionResponse)
                            .toList();
                    result.put(Constants.FIELDS, fields);
                    log.info(Constants.LOG_FORM_V1_FIELDS, formId, fields.size());
                } else {
                    log.warn("getFormById formId={} version=1 no questions found", formId);
                    result.put(Constants.FIELDS, Collections.emptyList());
                }
            } else {
                log.info(LOG_FORM_V2_JSONB, formId);
            }
            formCacheService.put(formId, result);
            return buildSuccess(response, result);

        } catch (Exception e) {
            log.error(LOG_FORM_ERROR, formId, e.getMessage(), e);
            return buildError(response,
                    ERR_INTERNAL + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
