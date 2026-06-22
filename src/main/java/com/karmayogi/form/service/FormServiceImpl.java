package com.karmayogi.form.service;

import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.entity.FormQuestions;
import com.karmayogi.form.entity.Forms;
import com.karmayogi.form.model.*;
import com.karmayogi.form.repository.FormQuestionsRepository;
import com.karmayogi.form.repository.FormRepository;
import com.karmayogi.form.utils.*;
import com.karmayogi.form.validator.BaseFormValidator;
import com.karmayogi.form.validator.ValidatorRegistry;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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
    private final ValidatorRegistry validatorRegistry;
    private final FormEntityMapper formEntityMapper;
    private final FormEventPublisher formEventPublisher;
    private final FormUpdateHelper formUpdateHelper;
    private final FormCreateHelper formCreateHelper;
    private final FormSearchHelper formSearchHelper;
    private final FormSubmissionHelper formSubmissionHelper;

    @Override
    public Map<String, Object> searchForms(Map<String, Object> request) {
        return Map.of();
    }

    public ApiResponse getFormById(String formId) {

        ApiResponse response = new ApiResponse(API_GET_FORM_BY_ID);

        if (StringUtils.isBlank(formId)) {
            log.warn(LOG_FORM_ID_BLANK);
            return buildError(response,
                    ERR_FORM_ID_REQUIRED,
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
                log.warn(LOG_FORM_NOT_FOUND, formId);
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
                    result.put(FIELDS, fields);
                    log.info(LOG_FORM_V1_FIELDS, formId, fields.size());
                } else {
                    log.warn("getFormById formId={} version=1 no questions found", formId);
                    result.put(FIELDS, Collections.emptyList());
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

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ApiResponse createForm(FormRequest request, String userId) {

        ApiResponse response = new ApiResponse(API_CREATE_FORM);
        String formId = UUID.randomUUID().toString();

        try {
            log.info("createForm started formId={} userId={} contextType={}",
                    formId, userId, request.getContextType());
            BaseFormValidator validator = validatorRegistry.getValidator(request.getContextType());
            if (validator == null) {
                log.warn("createForm no validator found for contextType={}",
                        request.getContextType());
                return buildError(response,
                        ERR_INVALID_CONTEXT_TYPE, HttpStatus.BAD_REQUEST);
            }

            String error = validator.validate(request, userId);
            if (error != null) {
                log.warn("createForm validation failed formId={}: {}", formId, error);
                return buildError(response, error, HttpStatus.BAD_REQUEST);
            }

            formEntityMapper.setFormStatus(request);

            boolean isV2 = request.getClientVersion() != null
                    && request.getClientVersion() >= formConfig.getV2ClientVersion();

            Forms form = formEntityMapper.toFormsEntity(
                    request, formId, userId, request.getContextType());

            if (isV2) {
                formCreateHelper.prepareV2Form(form, request, formId);
                log.info("createForm version=2 JSONB formId={}", formId);
            } else {
                formCreateHelper.prepareV1Form(form);
                log.info("createForm version=1 form_questions formId={}", formId);
            }

            if (!ASSIGNMENT.equals(request.getContextType())) {
                formEntityMapper.addMandatoryFields(form);
            }

            formRepository.saveAndFlush(form);
            log.info("createForm forms saved formId={}", formId);
            formCreateHelper.saveV1Questions(request, formId, isV2);
            formEventPublisher.publishFormCreated(form, request.getFields());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put(FORM_ID, formId);
            data.put(VERSION, isV2 ? 2 : 1);
            data.put(CLIENT_VERSION, isV2 ? formConfig.getV2ClientVersion() : formConfig.getV1ClientVersion());
            if (CollectionUtils.isNotEmpty(request.getFields())) {
                data.put(TOTAL_FIELDS, request.getFields().size());
            }

            log.info("createForm success formId={} version={} fields={}",
                    formId, isV2 ? 2 : 1,
                    CollectionUtils.isEmpty(request.getFields())
                            ? 0 : request.getFields().size());
            return buildSuccess(response, data);

        } catch (DataIntegrityViolationException e) {
           throw e;
        } catch (Exception e) {
            log.error("createForm error formId={}: {}", formId, e.getMessage(), e);
            return buildError(response,
                    ERR_INTERNAL + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    @Override
    public ApiResponse updateForm(FormRequest request, String userId) {

        ApiResponse response = new ApiResponse(API_UPDATE_FORM);
        String formId = request.getFormId();

        try {
            log.info("updateForm started formId={} userId={}", formId, userId);

            if (StringUtils.isBlank(formId)) {
                return buildError(response, ERR_FORM_ID_REQUIRED, HttpStatus.BAD_REQUEST);
            }

            Optional<Forms> formOpt = formRepository.findById(formId);
            if (formOpt.isEmpty()) {
                log.warn("updateForm formId={} not found", formId);
                return buildError(response,
                        ERR_FORM_NOT_FOUND + formId, HttpStatus.NOT_FOUND);
            }
            Forms form = formOpt.get();

            BaseFormValidator validator = validatorRegistry.getValidator(form.getContextType());
            if (validator == null) {
                return buildError(response, ERR_INVALID_CONTEXT_TYPE, HttpStatus.BAD_REQUEST);
            }
            String error = validator.validate(request, userId);
            if (error != null) {
                log.warn("updateForm validation failed formId={}: {}", formId, error);
                return buildError(response, error, HttpStatus.BAD_REQUEST);
            }

            formEntityMapper.applyUpdateFields(form, request, userId);

            FieldUpdateResult fieldResult = formUpdateHelper.updateFields(form, request);

            formRepository.saveAndFlush(form);
            log.info("updateForm form saved formId={}", formId);
            formCacheService.invalidate(formId);
            formEventPublisher.publishFormUpdated(form, request.getFields(), fieldResult.staleFieldIds());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put(FORM_ID, formId);
            data.put(CREATED_FIELDS_COUNT, fieldResult.createdCount());
            data.put(UPDATED_FIELDS_COUNT, fieldResult.updatedCount());
            data.put(DELETED_FIELDS_COUNT, fieldResult.deletedCount());

            log.info("updateForm success formId={} created={} updated={} deleted={}",
                    formId, fieldResult.createdCount(),
                    fieldResult.updatedCount(), fieldResult.deletedCount());
            return buildSuccess(response, data);

        } catch (DataIntegrityViolationException e) {
          throw e;
        } catch (Exception e) {
            log.error("updateForm error formId={}: {}", formId, e.getMessage(), e);
            return buildError(response,
                    ERR_INTERNAL + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse searchForms(SearchCriteria criteria) {

        ApiResponse response = new ApiResponse(API_SEARCH_FORM);

        try {
            log.info("searchForms started criteria={}", criteria);

            String filterError =
                    formSearchHelper.validateFilters(criteria.getFilters());

            if (filterError != null) {
                return buildError(response, filterError, HttpStatus.BAD_REQUEST);
            }

            SearchPageRequest pageRequest =
                    formSearchHelper.validateAndBuildPageRequest(criteria);

            if (pageRequest.error() != null) {
                return buildError(
                        response,
                        pageRequest.error(),
                        HttpStatus.BAD_REQUEST);
            }

            SearchResponse searchResponse =
                    formSearchHelper.search(criteria, pageRequest);

            SearchResultData resultData =
                    formSearchHelper.extractSearchResults(searchResponse);

            List<Map<String, Object>> finalResults =
                    formSearchHelper.enrichResults(
                            resultData.forms(),
                            resultData.formIds(),
                            criteria,
                            pageRequest.size());

            Map<String, Object> content = new HashMap<>();
            content.put(COUNT, searchResponse.getHits().getTotalHits());
            content.put(CONTENT, finalResults);

            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(RESPONSE, content);

            response.setResponse(responseMap);

            log.info("searchForms completed totalHits={} returned={}",
                    searchResponse.getHits().getTotalHits(),
                    finalResults.size());

            return response;

        } catch (Exception e) {
            log.error("searchForms error: {}", e.getMessage(), e);

            return buildError(
                    response,
                    ERR_INTERNAL + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse submitForm(FormSubmissionRequest request,
                                  String userId,
                                  boolean isAnonymousUser) {
        ApiResponse response = new ApiResponse(API_SUBMIT_FORM);
        try {
            log.info("submitForm started formId={} userId={} anonymous={}", request.getFormId(), userId, isAnonymousUser);

            Map<String, Object> userInfo = new HashMap<>();
            String error = formSubmissionHelper.validate(request, userId, userInfo, isAnonymousUser);
            if (error != null) {
                log.warn("submitForm validation failed formId={}: {}", request.getFormId(), error);
                return buildError(response, error, HttpStatus.BAD_REQUEST);
            }

            String status = StringUtils.isBlank(request.getStatus()) ? SUBMITTED : request.getStatus().toUpperCase();


            List<Map<String, Object>> normalizedResponses = formSubmissionHelper.normalizeResponses(request.getResponses());

            String contextType = StringUtils.isBlank(request.getContextType()) ? FORM : request.getContextType().trim();


            String submissionId = isAnonymousUser
                                    ? formSubmissionHelper.saveAnonymous(request, status, contextType, normalizedResponses)
                                    : formSubmissionHelper.saveAuthenticated(request, userId, status, contextType, normalizedResponses, userInfo);


            Map<String, Object> data = new LinkedHashMap<>();
            data.put(DOCUMENT_ID, submissionId);
            data.put(FORM_ID, request.getFormId());
            data.put(SAVED_STATUS, status);
            data.put(RESPONSES_COUNT, request.getResponses() != null ? request.getResponses().size() : 0);
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(RESPONSE, data);
            response.setResponse(responseMap);
            log.info("submitForm success formId={} submissionId={} status={}", request.getFormId(), submissionId, status);
            return response;

        } catch (Exception e) {
            log.error("submitForm error formId={}: {}", request.getFormId(),
                    e.getMessage(), e);
            return buildError(response,
                    "Error while submitting form: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse getUserSavedForm(String formId, String status, String userId, String contextId) {
        ApiResponse response = new ApiResponse(API_GET_SAVED_FORM);
        try {
            log.info("getUserSavedForm formId={} status={} userId={}",
                    formId, status, userId);

            String error = formSubmissionHelper.validateGetSavedForm(formId, contextId, userId);
            if (error != null)
                return buildError(response, error, HttpStatus.BAD_REQUEST);

            Map<String, Object> data = formSubmissionHelper.findSavedForm(formId, status, userId, contextId);

            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(RESPONSE, data != null ? data : new HashMap<>());
            response.setResponse(responseMap);

            log.info("getUserSavedForm completed formId={} userId={} found={}",
                    formId, userId, data != null);
            return response;

        } catch (Exception e) {
            log.error("getUserSavedForm error formId={} userId={}: {}",
                    formId, userId, e.getMessage(), e);
            return buildError(response,
                    ERR_INTERNAL + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
