package com.karmayogi.form.service;

import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.entity.FormQuestions;
import com.karmayogi.form.entity.FormSubmission;
import com.karmayogi.form.entity.Forms;
import com.karmayogi.form.exception.CustomException;
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
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ValidatorRegistry validatorRegistry;
    private final FormEntityMapper formEntityMapper;
    private final FormEventPublisher formEventPublisher;
    private final FormUpdateHelper formUpdateHelper;
    private final FormCreateHelper formCreateHelper;
    private final FormSearchHelper formSearchHelper;
    private final FormSubmissionHelper formSubmissionHelper;
    private final AccessTokenValidator accessTokenValidator;
    private final PeerSurveyHelper peerSurveyHelper;
    private final RestHighLevelClient client;

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

            if (PEER_VALIDATION_SURVEY.equalsIgnoreCase(request.getContextType())) {
                peerSurveyHelper.mergeSystemGeneratedQuestions(request);
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
            String error = validator.validateForUpdate(request, userId);
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

    @Override
    public ApiResponse getAllApplications(String formId) {
        ApiResponse response = new ApiResponse(API_GET_ALL_APPLICATIONS);
        try {
            log.info("getAllApplications formId={}", formId);

            if (StringUtils.isBlank(formId))
                return buildError(response, ERR_FORM_ID_REQUIRED, HttpStatus.BAD_REQUEST);


            List<Map<String, Object>> results = formSubmissionHelper.getAllApplications(formId);

            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(COUNT, results.size());
            responseMap.put(RESPONSE, results.isEmpty() ? new HashMap<>() : results);
            response.setResponse(responseMap);

            log.info("getAllApplications formId={} count={}", formId, results.size());
            return response;

        } catch (Exception e) {
            log.error("getAllApplications error formId={}: {}", formId, e.getMessage(), e);
            return buildError(response,
                    ERR_INTERNAL + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse searchUserFeedbackForms(SearchCriteria criteria) {
        ApiResponse response = new ApiResponse(API_SUBMISSION_SEARCH);
        try {
            log.info("searchUserFeedbackForms criteria={}", criteria);

            String error = formSubmissionHelper.validateSubmissionSearch(criteria);
            if (error != null)
                return buildError(response, error, HttpStatus.BAD_REQUEST);

            Map<String, Object> content = formSubmissionHelper.searchUserFeedbackForms(criteria, formConfig.getFormContextHideCreatorFields());

            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(RESPONSE, content);
            response.setResponse(responseMap);

            log.info("searchUserFeedbackForms completed count={}", content.get(COUNT));
            return response;

        } catch (Exception e) {
            log.error("searchUserFeedbackForms error: {}", e.getMessage(), e);
            return buildError(response, ERR_INTERNAL + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse saveFeedback(FeedbackRequest request, String userId) {
        ApiResponse response = new ApiResponse(API_FEEDBACK);
        try {
            log.info("saveFeedback started formId={} userId={}", request.getFormId(), userId);

            String error = formSubmissionHelper.validateFeedback(request, userId);
            if (error != null) {
                log.warn("saveFeedback validation failed: {}", error);
                return buildError(response, error, HttpStatus.BAD_REQUEST);
            }

            FormSubmission submission = formSubmissionHelper.saveFeedbackToSubmission(request, userId);

            if (submission == null) {
                return buildError(response,
                        "No submission found for formId=" + request.getFormId(),
                        HttpStatus.NOT_FOUND);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put(DOCUMENT_ID, submission.getSubmissionId());
            data.put(FORM_ID,     request.getFormId());
            data.put(SAVED_STATUS, submission.getStatus());
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(RESPONSE, data);
            response.setResponse(responseMap);

            log.info("saveFeedback success formId={} submissionId={}",
                    request.getFormId(), submission.getSubmissionId());
            return response;

        } catch (IllegalStateException e) {
            log.warn("saveFeedback invalid state formId={}: {}", request.getFormId(), e.getMessage());
            return buildError(response, e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("saveFeedback error formId={}: {}", request.getFormId(), e.getMessage(), e);
            return buildError(response,
                    "Error while submitting feedback: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse processAssignmentAnswer(FormSubmissionRequest request, String userId, boolean isDraft) {
        ApiResponse response = new ApiResponse(API_PROCESS_ASSIGNMENT);
        try {
            log.info("processAssignmentAnswer {} formId={} userId={}",
                    isDraft ? "PREVIEW" : "SUBMIT", request.getFormId(), userId);

            String error = formSubmissionHelper.validateAnswerForAssignment(request, userId);
            if (error != null)
                return buildError(response, error, HttpStatus.BAD_REQUEST);

            Optional<com.karmayogi.form.entity.Forms> formOpt = formRepository.findById(request.getFormId());
            if (formOpt.isEmpty())
                return buildError(response,
                        "Assignment not found for formId=" + request.getFormId(),
                        HttpStatus.BAD_REQUEST);

            com.karmayogi.form.entity.Forms form = formOpt.get();
            if (!Constants.ASSIGNMENT.equalsIgnoreCase(form.getContextType()))
                return buildError(response,
                        "Form is not of type assignment. formId=" + request.getFormId(),
                        HttpStatus.BAD_REQUEST);

            String courseId = form.getCourseId();

            String submissionId = isDraft
                    ? formSubmissionHelper.handlePreviewSubmission(request, userId, courseId)
                    : formSubmissionHelper.handleFinalSubmission(request, userId, courseId);

            if ("ALREADY_SUBMITTED".equals(submissionId)) {
                String msg = isDraft
                        ? "Assignment already submitted. Preview not allowed."
                        : "Assignment already submitted. Resubmission not allowed.";
                return buildError(response, msg, HttpStatus.BAD_REQUEST);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put(DOCUMENT_ID, submissionId);
            data.put(FORM_ID, request.getFormId());
            data.put(SAVED_STATUS, isDraft ? DRAFT : SUBMITTED_CAPS);
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(RESPONSE, data);
            response.setResponse(responseMap);

            log.info("processAssignmentAnswer success formId={} submissionId={} status={}",
                    request.getFormId(), submissionId,
                    isDraft ? DRAFT : SUBMITTED_CAPS);
            return response;

        } catch (Exception e) {
            log.error("processAssignmentAnswer error formId={}: {}",
                    request.getFormId(), e.getMessage(), e);
            return buildError(response,
                    "Error while processing assignment answer: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse getUserSavedFormsBulk(Map<String, Object> requestBody) {
        ApiResponse response = new ApiResponse(API_BULK_GET_APPLICATIONS);
        try {
            String userId = (String) requestBody.get(USER_ID_KEY);
            List<Map<String, String>> formContextList = (List<Map<String, String>>) requestBody.get(FORM_CONTEXT_LIST);

            log.info("getUserSavedFormsBulk userId={} count={}", userId, formContextList != null ? formContextList.size() : 0);

            if (StringUtils.isBlank(userId))
                return buildError(response, ERR_INVALID_USER_ID, HttpStatus.BAD_REQUEST);
            if (CollectionUtils.isEmpty(formContextList))
                return buildError(response, "Form context list cannot be empty", HttpStatus.BAD_REQUEST);

            List<Map<String, Object>> result = formSubmissionHelper.getUserSavedFormsBulk(userId, formContextList, formCacheService);


            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(RESPONSE, result);
            response.setResponse(responseMap);

            log.info("getUserSavedFormsBulk completed userId={} total={} found={}",
                    userId, formContextList.size(),
                    result.stream().filter(r -> Boolean.TRUE.equals(r.get(SUBMITTED))).count());
            return response;

        } catch (Exception e) {
            log.error("getUserSavedFormsBulk error: {}", e.getMessage(), e);
            return buildError(response,
                    ERR_INTERNAL + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse publicSubmitForm(PublicFormSubmissionRequest request) {
        ApiResponse response = new ApiResponse(API_PUBLIC_SUBMIT_FORM);
        try {
            log.info("publicSubmitForm formId={} emailId={}",
                    request != null ? request.getFormId() : null,
                    request != null ? request.getEmailId() : null);

            String error = formSubmissionHelper.validatePublicSubmission(request);
            if (error != null) {
                log.warn("publicSubmitForm validation failed: {}", error);
                return buildError(response, error, HttpStatus.BAD_REQUEST);
            }

            String statusValue = request.getStatus();

            String status = StringUtils.isBlank(statusValue)
                    ? SUBMITTED_CAPS
                    : statusValue.toUpperCase();

            String submissionId = formSubmissionHelper.savePublicForm(request, status);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put(DOCUMENT_ID, submissionId);
            data.put(FORM_ID, request.getFormId());
            data.put(SAVED_STATUS, status);
            data.put(RESPONSES_COUNT,
                    request.getResponses() != null ? request.getResponses().size() : 0);
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(RESPONSE, data);
            response.setResponse(responseMap);

            log.info("publicSubmitForm success formId={} submissionId={}", request.getFormId(), submissionId);
            return response;

        } catch (Exception e) {
            log.error("publicSubmitForm error: {}", e.getMessage(), e);
            return buildError(response,
                    "Error while submitting form: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse createPeerEvaluationSurveyForMDO(FormRequest form,
                                                        String token) {
        ApiResponse response = new ApiResponse(API_CREATE_PEER_SURVEY);
        try {
            Map<String, Object> tokenData = accessTokenValidator.verifyUserToken(token);
            if (MapUtils.isEmpty(tokenData))
                return buildError(response,
                        "Authentication failed: Invalid or expired access token.",
                        HttpStatus.UNAUTHORIZED);

            String userId  = (String) tokenData.get(USER_ID);
            String rootOrgId = peerSurveyHelper.fetchRecordsFromDB(KEYSPACE_SUNBIRD, USER, Map.of(ID, userId), Arrays.asList(ROOT_ORG_ID));
            String orgName = peerSurveyHelper.fetchRecordsFromDB(KEYSPACE_SUNBIRD, ORGANISATION, Map.of(ID, rootOrgId), Arrays.asList(ORG_NAME));

            form.setContextType(PEER_VALIDATION_SURVEY);

            CreatedFor createdFor = new CreatedFor();
            createdFor.setOrgId(rootOrgId);
            createdFor.setOrgName(orgName);
            form.setCreatedFor(List.of(createdFor));

            Map<String, Object> additionalProps = MapUtils.isEmpty(form.getAdditionalProperties()) ? new HashMap<>() : form.getAdditionalProperties();
            additionalProps.put(IS_SPV_CREATED, Boolean.FALSE);
            form.setAdditionalProperties(additionalProps);

            return createForm(form, userId);

        } catch (Exception e) {
            log.error("createPeerEvaluationSurveyForMDO error: {}", e.getMessage(), e);
            return buildError(response, ERR_INTERNAL + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse createPeerEvaluationSurveyForSPV(FormRequest form, String token) {
        ApiResponse response = new ApiResponse(API_CREATE_PEER_SURVEY);
        try {
            if (CollectionUtils.isEmpty(form.getCreatedFor()))
                return buildError(response,
                        "Invalid request: Organization ID (createdFor) cannot be empty.",
                        HttpStatus.BAD_REQUEST);

            Map<String, Object> tokenData = accessTokenValidator.verifyUserToken(token);
            if (MapUtils.isEmpty(tokenData))
                return buildError(response,
                        "Authentication failed: Invalid or expired access token.",
                        HttpStatus.UNAUTHORIZED);

            String userId = (String) tokenData.get(USER_ID);
            form.setContextType(PEER_VALIDATION_SURVEY);

            Map<String, Object> additionalProps = MapUtils.isEmpty(form.getAdditionalProperties()) ? new HashMap<>() : form.getAdditionalProperties();
            additionalProps.put(IS_SPV_CREATED, Boolean.TRUE);
            form.setAdditionalProperties(additionalProps);

            return createForm(form, userId);

        } catch (Exception e) {
            log.error("createPeerEvaluationSurveyForSPV error: {}", e.getMessage(), e);
            return buildError(response, ERR_INTERNAL + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse updatePeerSurvey(FormRequest request, String token) {
        ApiResponse response = new ApiResponse(API_UPDATE_PEER_SURVEY);
        try {
            Map<String, Object> tokenData = accessTokenValidator.verifyUserToken(token);
            if (MapUtils.isEmpty(tokenData))
                return buildError(response,
                        "Invalid or expired access token", HttpStatus.UNAUTHORIZED);

            String userId = (String) tokenData.get("userId");
            String rootOrgId = peerSurveyHelper.fetchRecordsFromDB(KEYSPACE_SUNBIRD, USER, Map.of(ID, userId), Arrays.asList(ROOT_ORG_ID));

            Optional<Forms> formOpt = formRepository.findById(request.getFormId());
            if (formOpt.isEmpty())
                return buildError(response, ERR_FORM_NOT_FOUND, HttpStatus.BAD_REQUEST);

            Forms existingSurvey = formOpt.get();

            String surveyOrgId = existingSurvey.getOrgId();
            if (!StringUtils.equals(surveyOrgId, rootOrgId))
                return buildError(response,
                        "User not authorized to update this survey",
                        HttpStatus.FORBIDDEN);

            if (!"Draft".equalsIgnoreCase(existingSurvey.getStatus()))
                return buildError(response,
                        "Survey can only be edited in Draft state",
                        HttpStatus.BAD_REQUEST);

            CreatedFor cf = new CreatedFor();
            cf.setOrgId(existingSurvey.getOrgId());
            cf.setOrgName(existingSurvey.getOrgName());
            request.setCreatedFor(List.of(cf));
            request.setStatus(null);


            String error = peerSurveyHelper.validatePeerSurveyUpdate(request);
            if (error != null)
                return buildError(response, error, HttpStatus.BAD_REQUEST);

            Long endDate = peerSurveyHelper.parseEndDate(request.getEndDate());
            String overlapError = peerSurveyHelper.validateActiveSurveyOverlap(
                    (String) request.getAdditionalProperties().get(Constants.IDENTIFIER),
                    request.getCreatedFor().get(0).getOrgId(),
                    System.currentTimeMillis(),
                    endDate,
                    request.getFormId());
            if (overlapError != null)
                return buildError(response, overlapError, HttpStatus.BAD_REQUEST);

            String systemError = peerSurveyHelper.validateSystemGeneratedProtection(request);
            if (systemError != null)
                return buildError(response, systemError, HttpStatus.BAD_REQUEST);

            return updateForm(request, userId);

        } catch (Exception e) {
            log.error("updatePeerSurvey error: {}", e.getMessage(), e);
            return buildError(response,
                    "Error while updating peer survey: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse searchPeerSurvey(SearchCriteria criteria, String token, boolean isSpvSearch) {
        List<String> orgIds = resolveOrgIds(criteria, token, isSpvSearch);
        return searchPeerSurveys(criteria, orgIds, isSpvSearch);
    }

    public List<String> resolveOrgIds(SearchCriteria criteria,
                                      String token,
                                      boolean isSpvSearch) {
        Map<String, Object> tokenData = accessTokenValidator.verifyUserToken(token);
        Map<String, Object> filters = criteria.getFilters();

        if (!isSpvSearch) {
            String userId = (String) tokenData.get(USER_ID);
            String rootOrgId = peerSurveyHelper.fetchRecordsFromDB(KEYSPACE_SUNBIRD, USER,
                    Map.of(ID, userId), List.of(ROOT_ORG_ID));
            return StringUtils.isNotBlank(rootOrgId)
                    ? List.of(rootOrgId)
                    : Collections.emptyList();
        }

        if (MapUtils.isEmpty(filters) || CollectionUtils.isEmpty((List<String>) filters.get("orgIds")))
            throw new CustomException("400", "orgIds is mandatory for spv search", HttpStatus.BAD_REQUEST);

        return ((List<?>) filters.get("orgIds")).stream()
                .map(Object::toString)
                .distinct()
                .collect(Collectors.toList());
    }

    public ApiResponse searchPeerSurveys(SearchCriteria criteria,
                                         List<String> orgIds,
                                         Boolean isSpvSearch) {
        ApiResponse apiResponse = new ApiResponse(API_PEER_SURVEY_SEARCH);
        try {
            String validation = peerSurveyHelper.validatePeerSurveySearch(criteria, orgIds);
            if (validation != null)
                return buildError(apiResponse, validation, HttpStatus.BAD_REQUEST);

            SearchSourceBuilder sourceBuilder = peerSurveyHelper.buildPeerSurveySourceBuilder(criteria, orgIds, isSpvSearch);
            SearchRequest searchRequest = new SearchRequest(formConfig.getFormIndexV2()).source(sourceBuilder);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

            Map<String, Object> response = peerSurveyHelper.buildPeerSurveyResponse(searchResponse, isSpvSearch);
            apiResponse.setResponse(Collections.singletonMap(RESPONSE, response));
            return apiResponse;

        } catch (Exception e) {
            log.error("searchPeerSurveys error: {}", e.getMessage(), e);
            return buildError(apiResponse, ERR_INTERNAL + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ApiResponse publishPeerSurvey(String formId, String token) {
        ApiResponse response = new ApiResponse(API_PUBLISH_PEER_SURVEY);
        try {
            Map<String, Object> tokenData = accessTokenValidator.verifyUserToken(token);
            if (MapUtils.isEmpty(tokenData))
                return buildError(response, ERR_INVALID_TOKEN,
                        HttpStatus.UNAUTHORIZED);

            String userOrgId = peerSurveyHelper.fetchRecordsFromDB(
                    KEYSPACE_SUNBIRD, USER,
                    Map.of(ID, (String) tokenData.get(USER_ID)),
                    List.of(ROOT_ORG_ID));

            Optional<Forms> formOpt = formRepository.findById(formId);
            if (formOpt.isEmpty())
                return buildError(response, ERR_FORM_NOT_FOUND, HttpStatus.BAD_REQUEST);

            Forms survey = formOpt.get();

            if (!DRAFT.equalsIgnoreCase(survey.getStatus()))
                return buildError(response,
                        SURVEY_PUBLISHED_ERROR_MESSAGE,
                        HttpStatus.BAD_REQUEST);

            if (!StringUtils.equals(survey.getOrgId(), userOrgId))
                return buildError(response,
                        PEER_SURVEY_UNAUTHORIZED,
                        HttpStatus.FORBIDDEN);

            String validationError = peerSurveyHelper.validatePublishPeerSurvey(survey);
            if (validationError != null)
                return buildError(response, validationError, HttpStatus.BAD_REQUEST);

            String identifier = (String) survey.getAdditionalProperties().get(Constants.IDENTIFIER);
            long startDate = survey.getStartDate() != null ? survey.getStartDate() : System.currentTimeMillis();
            String overlapError = peerSurveyHelper.validateActiveSurveyOverlap(identifier, survey.getOrgId(), startDate, survey.getEndDate(), formId);
            if (overlapError != null)
                return buildError(response,
                        SURVEY_EXITS,
                        HttpStatus.CONFLICT);

            long now = System.currentTimeMillis();
            survey.setStatus(ACTIVE);
            survey.setStartDate(now);
            survey.setUpdatedAt(now);
            formRepository.saveAndFlush(survey);

            formEventPublisher.publishFormUpdated(survey, null, null);

            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(MESSAGE, SURVEY_PUBLISHED_SUCCESSFULLY);
            response.setResponse(responseMap);

            log.info("publishPeerSurvey success formId={}", formId);
            return response;

        } catch (Exception e) {
            log.error("publishPeerSurvey error formId={}: {}", formId, e.getMessage(), e);
            return buildError(response, "Error while publishing survey: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        }
}
