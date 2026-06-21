package com.karmayogi.form.service;

import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.entity.FormQuestions;
import com.karmayogi.form.entity.Forms;
import com.karmayogi.form.model.FormRequest;
import com.karmayogi.form.repository.FormQuestionsRepository;
import com.karmayogi.form.repository.FormRepository;
import com.karmayogi.form.utils.*;
import com.karmayogi.form.validator.BaseFormValidator;
import com.karmayogi.form.validator.ValidatorRegistry;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
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
            log.info("createForm started formId={} contextType={}",
                    formId, request.getContextType());

            BaseFormValidator validator = validatorRegistry.getValidator(request.getContextType());
            String error = (validator != null) ? validator.validate(request, userId) : ERR_INVALID_CONTEXT_TYPE;
            if (StringUtils.isNotEmpty(error)) {
                log.warn("createForm validation failed formId={}: {}", formId, error);
                return buildError(response, error, HttpStatus.BAD_REQUEST);
            }

            formEntityMapper.setFormStatus(request);

            boolean isV2 = request.getClientVersion() != null
                    && request.getClientVersion() >= formConfig.getV2ClientVersion();

            Forms form = formEntityMapper.toFormsEntity(
                    request, formId, userId, request.getContextType());

            if (isV2) {
                form.setClientVersion(formConfig.getV2ClientVersion());
                if (CollectionUtils.isNotEmpty(request.getFields())) {
                    request.getFields().forEach(field -> {
                        if (StringUtils.isBlank(field.getId())) {
                            field.setId(UUID.randomUUID().toString());
                            field.setFormId(formId);
                        }
                    });
                    formEntityMapper.toFormQuestionsEntities(
                            request.getFields(),
                            formId,
                            request.getStatus());
                }
                form.setQuestions(request.getFields());
                log.info("createForm version=2 JSONB formId={}", formId);
            } else {
                form.setClientVersion(formConfig.getV1ClientVersion());
                form.setQuestions(null);
                log.info("createForm version=1 form_questions formId={}", formId);
            }

            if (!ASSIGNMENT.equals(request.getContextType())) {
                formEntityMapper.addMandatoryFields(form);
            }

            formRepository.saveAndFlush(form);
            log.info("createForm forms saved formId={}", formId);
            List<FormQuestions> questions = null;
            if (!isV2 && CollectionUtils.isNotEmpty(request.getFields())) {
                 questions = formEntityMapper.toFormQuestionsEntities(request.getFields(), formId, request.getStatus());
                formQuestionsRepository.saveAll(questions);
                log.info("createForm questions saved formId={} count={}",
                        formId, questions.size());
            }
            formEventPublisher.publishFormCreated(form, request.getFields());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put(FORM_ID, formId);
            data.put(CLIENT_VERSION, isV2 ? formConfig.getV2ClientVersion() : formConfig.getV1ClientVersion());
            if (CollectionUtils.isNotEmpty(request.getFields())) {
                data.put(TOTAL_FIELDS, request.getFields().size());
            }

            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(RESPONSE, data);
            response.setResponse(responseMap);

            log.info("createForm success formId={} version={} fields={}",
                    formId, isV2 ? formConfig.getV2ClientVersion() : formConfig.getV1ClientVersion(),
                    CollectionUtils.isEmpty(request.getFields())
                            ? 0 : request.getFields().size());
            return response;

        } catch (DataIntegrityViolationException e) {
            log.warn("createForm constraint violation formId={} cause={}",
                    formId,
                    e.getMostSpecificCause().getMessage());
            throw e;
        } catch (Exception e) {
            log.error("createForm error formId={}: {}", formId, e.getMessage(), e);
            return buildError(response,
                    ERR_INTERNAL + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
