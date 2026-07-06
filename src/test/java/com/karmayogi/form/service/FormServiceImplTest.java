package com.karmayogi.form.service;

import com.karmayogi.form.entity.FormQuestions;
import com.karmayogi.form.entity.FormSubmission;
import com.karmayogi.form.entity.Forms;
import com.karmayogi.form.model.*;
import com.karmayogi.form.repository.FormQuestionsRepository;
import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.utils.*;
import com.karmayogi.form.validator.ValidatorRegistry;
import com.karmayogi.form.validator.BaseFormValidator;
import com.karmayogi.form.repository.FormRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author anil
 */
@ExtendWith(MockitoExtension.class)
public class FormServiceImplTest {

    @Mock
    private FormRepository formRepository;

    @Mock
    private FormQuestionsRepository formQuestionsRepository;

    @Mock
    private FormCacheService formCacheService;

    @Mock
    private FormConfig formConfig;

    @Mock
    private ValidatorRegistry validatorRegistry;

    @Mock
    private BaseFormValidator baseFormValidator;

    @Mock
    private FormEntityMapper formEntityMapper;

    @Mock
    private FormEventPublisher formEventPublisher;

    @Mock
    private FormUpdateHelper formUpdateHelper;

    @Mock
    private FormCreateHelper formCreateHelper;

    @Mock
    private FormSubmissionHelper formSubmissionHelper;

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private PeerSurveyHelper peerSurveyHelper;

    @InjectMocks
    private FormServiceImpl formService;

    private static final String FORM_ID   = "1746684187181";
    private static final String FORM_TITLE = "Test Form";

    private Forms mockForm;
    private Forms mockFormV2;
    private FormQuestions mockQuestion;

    @BeforeEach
    void setUp() {
        mockForm = new Forms();
        mockForm.setFormId(FORM_ID);
        mockForm.setTitle(FORM_TITLE);
        mockForm.setStatus("PUBLISH");
        mockForm.setContextType("form");
        mockForm.setVersion(1);
        mockForm.setClientVersion(1.1);
        mockForm.setCreatedAt(1746684187181L);
        mockForm.setQuestions(null);
        mockFormV2 = new Forms();
        mockFormV2.setFormId(FORM_ID);
        mockFormV2.setTitle(FORM_TITLE);
        mockFormV2.setStatus("PUBLISH");
        mockFormV2.setContextType("form");
        mockFormV2.setVersion(2);
        mockFormV2.setClientVersion(2.0);
        mockFormV2.setQuestions(List.of(Map.of("name", "Question 1", "fieldType", "text")));
        mockQuestion = new FormQuestions();
        mockQuestion.setQuestionId("uuid-1");
        mockQuestion.setFormId(FORM_ID);
        mockQuestion.setName("What is your age");
        mockQuestion.setFieldType("text");
        mockQuestion.setQuestionOrder(1);
        mockQuestion.setIsRequired(true);
    }
    @Test
    void testGetFormById_fromCache_success() {
        Map<String, Object> cachedData = Map.of(
                "formId", FORM_ID,
                "title",  FORM_TITLE
        );
        when(formCacheService.get(FORM_ID)).thenReturn(cachedData);

        ApiResponse response = formService.getFormById(FORM_ID);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.STATUS_SUCCESS, response.getParams().getStatus());
        assertNotNull(response.get(Constants.RESPONSE));

        verify(formRepository, never()).findById(anyString());
        verify(formQuestionsRepository, never())
                .findByFormIdOrderByQuestionOrderAsc(anyString());
    }


    @Test
    void testGetFormById_cacheMiss_version1_withQuestions_success() {
        when(formCacheService.get(FORM_ID)).thenReturn(null);
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(mockForm));
        when(formQuestionsRepository.findByFormIdOrderByQuestionOrderAsc(FORM_ID))
                .thenReturn(List.of(mockQuestion));

        ApiResponse response = formService.getFormById(FORM_ID);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.STATUS_SUCCESS, response.getParams().getStatus());
        assertNotNull(response.get(Constants.RESPONSE));

        Map<String, Object> result = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertNotNull(result.get(Constants.FIELDS));
        assertFalse(((List<?>) result.get(Constants.FIELDS)).isEmpty());

        verify(formCacheService, times(1)).put(eq(FORM_ID), any());
    }


    @Test
    void testGetFormById_cacheMiss_version1_noQuestions_success() {
        when(formCacheService.get(FORM_ID)).thenReturn(null);
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(mockForm));
        when(formQuestionsRepository.findByFormIdOrderByQuestionOrderAsc(FORM_ID))
                .thenReturn(Collections.emptyList());

        ApiResponse response = formService.getFormById(FORM_ID);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.STATUS_SUCCESS, response.getParams().getStatus());

        Map<String, Object> result = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertNotNull(result.get(Constants.FIELDS));
        assertTrue(((List<?>) result.get(Constants.FIELDS)).isEmpty());
    }


    @Test
    void testGetFormById_cacheMiss_version2_jsonb_success() {
        when(formCacheService.get(FORM_ID)).thenReturn(null);
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(mockFormV2));

        ApiResponse response = formService.getFormById(FORM_ID);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.STATUS_SUCCESS, response.getParams().getStatus());
        assertNotNull(response.get(Constants.RESPONSE));

        verify(formQuestionsRepository, never())
                .findByFormIdOrderByQuestionOrderAsc(anyString());

        verify(formCacheService, times(1)).put(eq(FORM_ID), any());
    }


    @Test
    void testGetFormById_formNotFound_returns404() {
        when(formCacheService.get(FORM_ID)).thenReturn(null);
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.empty());

        ApiResponse response = formService.getFormById(FORM_ID);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.STATUS_FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg()
                .contains(Constants.ERR_FORM_NOT_FOUND));

        verify(formCacheService, never()).put(anyString(), any());
    }


    @Test
    void testGetFormById_blankFormId_returns400() {
        ApiResponse response = formService.getFormById("");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.STATUS_FAILED, response.getParams().getStatus());
        assertEquals(Constants.ERR_FORM_ID_REQUIRED,
                response.getParams().getErrMsg());
        verify(formCacheService, never()).get(anyString());
        verify(formRepository,   never()).findById(anyString());
    }


    @Test
    void testGetFormById_nullFormId_returns400() {
        ApiResponse response = formService.getFormById(null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.STATUS_FAILED, response.getParams().getStatus());
        assertEquals(Constants.ERR_FORM_ID_REQUIRED,
                response.getParams().getErrMsg());
    }

    @Test
    void testGetFormById_repositoryException_returns500() {
        when(formCacheService.get(FORM_ID)).thenReturn(null);
        when(formRepository.findById(FORM_ID))
                .thenThrow(new RuntimeException("DB connection failed"));

        ApiResponse response = formService.getFormById(FORM_ID);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.STATUS_FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg()
                .contains(Constants.ERR_INTERNAL));
    }

    @Test
    void testGetFormById_cacheException_fallbackToPostgres_success() {
        when(formCacheService.get(FORM_ID))
                .thenThrow(new RuntimeException("Redis connection failed"));

        ApiResponse response = formService.getFormById(FORM_ID);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.STATUS_FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg()
                .contains(Constants.ERR_INTERNAL));

        verify(formRepository, never()).findById(anyString());
    }


    @Test
    void testGetFormById_version1_nullQuestionSkipped() {
        when(formCacheService.get(FORM_ID)).thenReturn(null);
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(mockForm));

        List<FormQuestions> questions = new ArrayList<>();
        questions.add(mockQuestion);
        questions.add(null);

        when(formQuestionsRepository.findByFormIdOrderByQuestionOrderAsc(FORM_ID)).thenReturn(questions);

        ApiResponse response = formService.getFormById(FORM_ID);

        assertEquals(HttpStatus.OK, response.getResponseCode());

        Map<String, Object> result = (Map<String, Object>) response.get(Constants.RESPONSE);
        List<?> fields = (List<?>) result.get(Constants.FIELDS);

        assertEquals(1, fields.size());
    }


    @Test
    void testCreateForm_v1_withFields_success() throws Exception {
        FormRequest request = new FormRequest();
        request.setTitle("My Form");
        request.setStatus("PUBLISH");
        request.setContextType("form");

        FieldMeta f = new FieldMeta();
        f.setName("Question 1");
        f.setFieldType("text");
        f.setOrder(1);
        f.setIsRequired(true);
        request.setFields(List.of(f));

        when(validatorRegistry.getValidator("form")).thenReturn(baseFormValidator);
        when(baseFormValidator.validate(any(FormRequest.class), anyString())).thenReturn(null);
        when(formConfig.getV1ClientVersion()).thenReturn(1.1);

        when(formEntityMapper.toFormsEntity(any(), anyString(), anyString(), anyString()))
                .thenReturn(mockForm);
        doNothing().when(formCreateHelper).prepareV1Form(any(Forms.class));

        when(formRepository.saveAndFlush(any(Forms.class))).thenReturn(mockForm);

        ApiResponse response = formService.createForm(request, "user-1");

        Map<String, Object> resp = response.getResult();
        assertNotNull(resp.get(Constants.RESPONSE));
        Map<String, Object> data = (Map<String, Object>) resp.get(Constants.RESPONSE);
        assertTrue(data.containsKey(Constants.FORM_ID));
        // clientVersion is stored as Number (Double) in the response map - compare numerically
        assertEquals(1.1, ((Number) data.get(Constants.CLIENT_VERSION)).doubleValue());
        assertEquals(1, ((Number) data.get(Constants.TOTAL_FIELDS)).intValue());

        verify(formRepository, times(1)).saveAndFlush(any(Forms.class));
        verify(formEventPublisher, times(1)).publishFormCreated(any(), any());
    }


    @Test
    void testCreateForm_v2_withFields_success() throws Exception {
        FormRequest request = new FormRequest();
        request.setTitle("My Form V2");
        request.setStatus("PUBLISH");
        request.setContextType("form");
        request.setClientVersion(2.0f);

        FieldMeta f = new FieldMeta();
        f.setName("Q1");
        f.setFieldType("text");
        f.setOrder(1);
        request.setFields(List.of(f));

        when(validatorRegistry.getValidator("form")).thenReturn(baseFormValidator);
        when(baseFormValidator.validate(any(FormRequest.class), anyString())).thenReturn(null);
        when(formConfig.getV2ClientVersion()).thenReturn(1.2);

        when(formEntityMapper.toFormsEntity(any(), anyString(), anyString(), anyString()))
                .thenReturn(mockFormV2);
        doNothing().when(formCreateHelper).prepareV2Form(any(Forms.class), any(FormRequest.class), anyString());

        when(formRepository.saveAndFlush(any(Forms.class))).thenReturn(mockFormV2);

        ApiResponse response = formService.createForm(request, "user-2");

        Map<String, Object> resp = response.getResult();
        assertNotNull(resp.get(Constants.RESPONSE));
        Map<String, Object> data = (Map<String, Object>) resp.get(Constants.RESPONSE);
        assertTrue(data.containsKey(Constants.FORM_ID));
        assertEquals(1.2, ((Number) data.get(Constants.CLIENT_VERSION)).doubleValue());
        assertEquals(1, ((Number) data.get(Constants.TOTAL_FIELDS)).intValue());

        verify(formRepository, times(1)).saveAndFlush(any(Forms.class));
        verify(formQuestionsRepository, never()).saveAll(any());
        verify(formEventPublisher, times(1)).publishFormCreated(any(), any());
    }

    @Test
    void testUpdateForm_success_allFieldsChanged(){
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        request.setTitle("Updated Form");
        request.setStatus("DRAFT");

        FieldMeta field1 = new FieldMeta();
        field1.setName("Updated Question 1");
        field1.setFieldType("text");
        field1.setOrder(1);
        request.setFields(List.of(field1));

        FieldUpdateResult fieldResult = new FieldUpdateResult(1, 2, 1, Set.of());

        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(mockForm));
        when(validatorRegistry.getValidator("form")).thenReturn(baseFormValidator);
        when(formUpdateHelper.updateFields(any(Forms.class), any(FormRequest.class))).thenReturn(fieldResult);
        when(formRepository.saveAndFlush(any(Forms.class))).thenReturn(mockForm);

        ApiResponse response = formService.updateForm(request, "user-1");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.STATUS_SUCCESS, response.getParams().getStatus());

        Map<String, Object> result = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertNotNull(result);
        assertEquals(FORM_ID, result.get(Constants.FORM_ID));
        assertEquals(1, result.get(Constants.CREATED_FIELDS_COUNT));
        assertEquals(2, result.get(Constants.UPDATED_FIELDS_COUNT));
        assertEquals(1, result.get(Constants.DELETED_FIELDS_COUNT));

        verify(formRepository, times(1)).saveAndFlush(any(Forms.class));
        verify(formCacheService, times(1)).invalidate(FORM_ID);
        verify(formEventPublisher, times(1)).publishFormUpdated(any(Forms.class), any(), any());
    }

    @Test
    void testUpdateForm_blankFormId_returns400() {
        FormRequest request = new FormRequest();
        request.setFormId("");
        request.setTitle("Updated Form");

        ApiResponse response = formService.updateForm(request, "user-1");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.STATUS_FAILED, response.getParams().getStatus());
        assertEquals(Constants.ERR_FORM_ID_REQUIRED,
                response.getParams().getErrMsg());

        verify(formRepository, never()).findById(anyString());
    }

    @Test
    void testUpdateForm_nullFormId_returns400() {
        FormRequest request = new FormRequest();
        request.setFormId(null);
        request.setTitle("Updated Form");

        ApiResponse response = formService.updateForm(request, "user-1");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.STATUS_FAILED, response.getParams().getStatus());
        assertEquals(Constants.ERR_FORM_ID_REQUIRED,
                response.getParams().getErrMsg());
    }

    @Test
    void testUpdateForm_formNotFound_returns404() {
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        request.setTitle("Updated Form");

        when(formRepository.findById(FORM_ID)).thenReturn(Optional.empty());

        ApiResponse response = formService.updateForm(request, "user-1");

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.STATUS_FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg()
                .contains(Constants.ERR_FORM_NOT_FOUND));

        verify(validatorRegistry, never()).getValidator(anyString());
    }

    @Test
    void testUpdateForm_invalidContextType_returns400() {
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        request.setTitle("Updated Form");

        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(mockForm));
        when(validatorRegistry.getValidator("form")).thenReturn(null);

        ApiResponse response = formService.updateForm(request, "user-1");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.STATUS_FAILED, response.getParams().getStatus());
        assertEquals(Constants.ERR_INVALID_CONTEXT_TYPE,
                response.getParams().getErrMsg());

        verify(formUpdateHelper, never()).updateFields(any(), any());
    }

    @Test
    void testUpdateForm_validationFailed_returns400() throws Exception {
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        request.setTitle("Updated Form");

        String validationError = "Title is required and cannot be empty";

        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(mockForm));
        when(validatorRegistry.getValidator("form")).thenReturn(baseFormValidator);
        when(baseFormValidator.validateForUpdate(any(FormRequest.class), anyString()))
                .thenReturn(validationError); // ← validateForUpdate not validate

        ApiResponse response = formService.updateForm(request, "user-1");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.STATUS_FAILED, response.getParams().getStatus());
        assertEquals(validationError, response.getParams().getErrMsg());

        verify(formUpdateHelper, never()).updateFields(any(), any());
        verify(formRepository, never()).saveAndFlush(any());
    }

    @Test
    void testUpdateForm_noFieldChanges() {
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        request.setTitle("Updated Form");
        request.setFields(Collections.emptyList());

        FieldUpdateResult fieldResult = new FieldUpdateResult(0, 0, 0, Set.of());

        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(mockForm));
        when(validatorRegistry.getValidator("form")).thenReturn(baseFormValidator);
        when(formUpdateHelper.updateFields(any(Forms.class), any(FormRequest.class))).thenReturn(fieldResult);
        when(formRepository.saveAndFlush(any(Forms.class))).thenReturn(mockForm);

        ApiResponse response = formService.updateForm(request, "user-1");

        assertEquals(HttpStatus.OK, response.getResponseCode());

        Map<String, Object> result = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertEquals(0, result.get(Constants.CREATED_FIELDS_COUNT));
        assertEquals(0, result.get(Constants.UPDATED_FIELDS_COUNT));
        assertEquals(0, result.get(Constants.DELETED_FIELDS_COUNT));

        verify(formCacheService, times(1)).invalidate(FORM_ID);
    }

    @Test
    void testUpdateForm_repositoryException_returns500() {
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        request.setTitle("Updated Form");

        when(formRepository.findById(FORM_ID)).thenThrow(
                new RuntimeException("Database connection failed"));

        ApiResponse response = formService.updateForm(request, "user-1");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.STATUS_FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg()
                .contains(Constants.ERR_INTERNAL));
    }

    @Test
    void testUpdateForm_dataIntegrityViolation_throwsException(){
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        request.setTitle("Updated Form");

        FieldUpdateResult fieldResult = new FieldUpdateResult(0, 1, 0, Set.of());

        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(mockForm));
        when(validatorRegistry.getValidator("form")).thenReturn(baseFormValidator);
        when(formUpdateHelper.updateFields(any(Forms.class), any(FormRequest.class))).thenReturn(fieldResult);
        when(formRepository.saveAndFlush(any(Forms.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violated"));

        assertThrows(DataIntegrityViolationException.class, () ->
                formService.updateForm(request, "user-1"));
    }

    @Test
    void testUpdateForm_multipleFieldsUpdate_success() {
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        request.setTitle("Multi-field Update");

        FieldMeta field1 = new FieldMeta();
        field1.setName("Q1");
        field1.setFieldType("text");
        field1.setOrder(1);

        FieldMeta field2 = new FieldMeta();
        field2.setName("Q2");
        field2.setFieldType("select");
        field2.setOrder(2);

        request.setFields(List.of(field1, field2));

        FieldUpdateResult fieldResult = new FieldUpdateResult(2, 3, 1, Set.of("stale-1"));

        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(mockForm));
        when(validatorRegistry.getValidator("form")).thenReturn(baseFormValidator);
        when(formUpdateHelper.updateFields(any(Forms.class), any(FormRequest.class))).thenReturn(fieldResult);
        when(formRepository.saveAndFlush(any(Forms.class))).thenReturn(mockForm);

        ApiResponse response = formService.updateForm(request, "user-1");

        assertEquals(HttpStatus.OK, response.getResponseCode());

        Map<String, Object> result = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertEquals(2, result.get(Constants.CREATED_FIELDS_COUNT));
        assertEquals(3, result.get(Constants.UPDATED_FIELDS_COUNT));
        assertEquals(1, result.get(Constants.DELETED_FIELDS_COUNT));

        verify(formEventPublisher, times(1))
                .publishFormUpdated(any(Forms.class), any(), any());
    }

    @Test
    void testPublicSubmitForm_success() {
        PublicFormSubmissionRequest request = new PublicFormSubmissionRequest();
        request.setFormId("form-123");
        request.setEmailId("test@example.com");
        request.setContextId("ctx-123");
        request.setContextName("Test Context");
        request.setStatus("SUBMITTED");
        request.setResponses(List.of(
                QuestionResponse.builder()
                        .questionId("q1")
                        .question("Test Question")
                        .answer("Test Answer")
                        .answerType("text")
                        .build()
        ));

        when(formSubmissionHelper.validatePublicSubmission(request)).thenReturn(null);
        when(formSubmissionHelper.savePublicForm(request, "SUBMITTED"))
                .thenReturn("submission-123");

        ApiResponse response = formService.publicSubmitForm(request);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.STATUS_SUCCESS, response.getParams().getStatus());
        Map<String, Object> data = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertEquals("submission-123", data.get(Constants.DOCUMENT_ID));
        assertEquals("form-123", data.get(Constants.FORM_ID));
        assertEquals("SUBMITTED", data.get(Constants.SAVED_STATUS));
        assertEquals(1, data.get(Constants.RESPONSES_COUNT));
    }

    @Test
    void testPublicSubmitForm_validationFailed_returns400() {
        PublicFormSubmissionRequest request = new PublicFormSubmissionRequest();
        request.setFormId("form-123");

        when(formSubmissionHelper.validatePublicSubmission(request))
                .thenReturn("emailId is required");

        ApiResponse response = formService.publicSubmitForm(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.STATUS_FAILED, response.getParams().getStatus());
        assertEquals("emailId is required", response.getParams().getErrMsg());

        verify(formSubmissionHelper, never()).savePublicForm(any(), anyString());
    }

    @Test
    void testPublicSubmitForm_nullRequest_returns400() {
        PublicFormSubmissionRequest request = new PublicFormSubmissionRequest();

        when(formSubmissionHelper.validatePublicSubmission(request))
                .thenReturn("Request is required");

        ApiResponse response = formService.publicSubmitForm(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Request is required", response.getParams().getErrMsg());
    }

    @Test
    void testPublicSubmitForm_blankStatus_defaultsToSubmitted() {
        PublicFormSubmissionRequest request = new PublicFormSubmissionRequest();
        request.setFormId("form-123");
        request.setEmailId("test@example.com");
        request.setStatus("");

        when(formSubmissionHelper.validatePublicSubmission(request)).thenReturn(null);
        when(formSubmissionHelper.savePublicForm(request, "SUBMITTED"))
                .thenReturn("submission-123");

        ApiResponse response = formService.publicSubmitForm(request);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> data = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertEquals("SUBMITTED", data.get(Constants.SAVED_STATUS));
    }

    @Test
    void testPublicSubmitForm_draftStatus() {
        PublicFormSubmissionRequest request = new PublicFormSubmissionRequest();
        request.setFormId("form-123");
        request.setEmailId("test@example.com");
        request.setStatus("DRAFT");

        when(formSubmissionHelper.validatePublicSubmission(request)).thenReturn(null);
        when(formSubmissionHelper.savePublicForm(request, "DRAFT"))
                .thenReturn("submission-456");

        ApiResponse response = formService.publicSubmitForm(request);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> data = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertEquals("DRAFT", data.get(Constants.SAVED_STATUS));
    }

    @Test
    void testPublicSubmitForm_nullResponses_returnsZeroCount() {
        PublicFormSubmissionRequest request = new PublicFormSubmissionRequest();
        request.setFormId("form-123");
        request.setEmailId("test@example.com");
        request.setStatus("SUBMITTED");
        request.setResponses(null);

        when(formSubmissionHelper.validatePublicSubmission(request)).thenReturn(null);
        when(formSubmissionHelper.savePublicForm(request, "SUBMITTED"))
                .thenReturn("submission-123");

        ApiResponse response = formService.publicSubmitForm(request);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> data = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertEquals(0, data.get(Constants.RESPONSES_COUNT));
    }

    @Test
    void testPublicSubmitForm_repositoryException_returns500() {
        PublicFormSubmissionRequest request = new PublicFormSubmissionRequest();
        request.setFormId("form-123");
        request.setEmailId("test@example.com");
        request.setStatus("SUBMITTED");

        when(formSubmissionHelper.validatePublicSubmission(request)).thenReturn(null);
        when(formSubmissionHelper.savePublicForm(any(), anyString()))
                .thenThrow(new RuntimeException("DB connection failed"));

        ApiResponse response = formService.publicSubmitForm(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.STATUS_FAILED, response.getParams().getStatus());
    }

    @Test
    void testGetUserSavedForm_success() {
        String formId = "form-123";
        String status = "SUBMITTED";
        String userId = "user-123";
        String contextId = "ctx-123";

        Map<String, Object> savedForm = Map.of(
                "formId", formId,
                "status", status,
                "userId", userId
        );

        when(formSubmissionHelper.validateGetSavedForm(formId, contextId, userId))
                .thenReturn(null);
        when(formSubmissionHelper.findSavedForm(formId, status, userId, contextId))
                .thenReturn(savedForm);

        ApiResponse response = formService.getUserSavedForm(formId, status, userId, contextId);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.STATUS_SUCCESS, response.getParams().getStatus());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @Test
    void testGetUserSavedForm_validationFailed_returns400() {
        String formId = "";
        String userId = "user-123";
        String contextId = "ctx-123";

        when(formSubmissionHelper.validateGetSavedForm(formId, contextId, userId))
                .thenReturn(Constants.ERR_FORM_ID_REQUIRED);

        ApiResponse response = formService.getUserSavedForm(formId, null, userId, contextId);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.ERR_FORM_ID_REQUIRED, response.getParams().getErrMsg());

        verify(formSubmissionHelper, never()).findSavedForm(any(), any(), any(), any());
    }

    @Test
    void testGetUserSavedForm_notFound_returnsEmptyMap() {
        String formId = "form-123";
        String userId = "user-123";
        String contextId = "ctx-123";

        when(formSubmissionHelper.validateGetSavedForm(formId, contextId, userId))
                .thenReturn(null);
        when(formSubmissionHelper.findSavedForm(formId, null, userId, contextId))
                .thenReturn(null);

        ApiResponse response = formService.getUserSavedForm(formId, null, userId, contextId);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> data = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertTrue(data.isEmpty());
    }

    @Test
    void testGetUserSavedForm_repositoryException_returns500() {
        String formId = "form-123";
        String userId = "user-123";
        String contextId = "ctx-123";

        when(formSubmissionHelper.validateGetSavedForm(formId, contextId, userId))
                .thenReturn(null);
        when(formSubmissionHelper.findSavedForm(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = formService.getUserSavedForm(formId, null, userId, contextId);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.STATUS_FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg().contains(Constants.ERR_INTERNAL));
    }

    @Test
    void testGetAllApplications_success() {
        String formId = "form-123";

        List<Map<String, Object>> results = List.of(
                Map.of("submissionId", "sub-1", "userId", "user-1", "status", "SUBMITTED"),
                Map.of("submissionId", "sub-2", "userId", "user-2", "status", "SUBMITTED")
        );

        when(formSubmissionHelper.getAllApplications(formId)).thenReturn(results);

        ApiResponse response = formService.getAllApplications(formId);

        assertNull(response.getResponseCode()); // no buildSuccess called
        Map<String, Object> responseMap = response.getResult();
        assertEquals(2, responseMap.get(Constants.COUNT));
        assertNotNull(responseMap.get(Constants.RESPONSE));
    }

    @Test
    void testGetAllApplications_blankFormId_returns400() {
        ApiResponse response = formService.getAllApplications("");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.ERR_FORM_ID_REQUIRED, response.getParams().getErrMsg());

        verify(formSubmissionHelper, never()).getAllApplications(anyString());
    }

    @Test
    void testGetAllApplications_nullFormId_returns400() {
        ApiResponse response = formService.getAllApplications(null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.ERR_FORM_ID_REQUIRED, response.getParams().getErrMsg());
    }

    @Test
    void testGetAllApplications_noResults_returnsEmptyMap() {
        String formId = "form-123";

        when(formSubmissionHelper.getAllApplications(formId))
                .thenReturn(Collections.emptyList());

        ApiResponse response = formService.getAllApplications(formId);

        assertNull(response.getResponseCode());
        Map<String, Object> responseMap = response.getResult();
        assertEquals(0, responseMap.get(Constants.COUNT));
    }

    @Test
    void testGetAllApplications_repositoryException_returns500() {
        String formId = "form-123";

        when(formSubmissionHelper.getAllApplications(formId))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = formService.getAllApplications(formId);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains(Constants.ERR_INTERNAL));
    }

    @Test
    void testGetUserSavedFormsBulk_success() {
        String userId = "user-123";
        List<Map<String, String>> formContextList = List.of(
                Map.of("formId", "form-1", "contextId", "ctx-1"),
                Map.of("formId", "form-2", "contextId", "ctx-2")
        );

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.USER_ID_KEY, userId);
        requestBody.put(Constants.FORM_CONTEXT_LIST, formContextList);

        List<Map<String, Object>> result = List.of(
                Map.of("formId", "form-1", "contextId", "ctx-1", "submitted", true),
                Map.of("formId", "form-2", "contextId", "ctx-2", "submitted", false)
        );

        when(formSubmissionHelper.getUserSavedFormsBulk(eq(userId), eq(formContextList), any()))
                .thenReturn(result);

        ApiResponse response = formService.getUserSavedFormsBulk(requestBody);

        assertNull(response.getResponseCode());
        Map<String, Object> responseMap = response.getResult();
        assertNotNull(responseMap.get(Constants.RESPONSE));
        List<?> responseList = (List<?>) responseMap.get(Constants.RESPONSE);
        assertEquals(2, responseList.size());
    }

    @Test
    void testGetUserSavedFormsBulk_blankUserId_returns400() {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.USER_ID_KEY, "");
        requestBody.put(Constants.FORM_CONTEXT_LIST, List.of(
                Map.of("formId", "form-1", "contextId", "ctx-1")
        ));

        ApiResponse response = formService.getUserSavedFormsBulk(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.ERR_INVALID_USER_ID, response.getParams().getErrMsg());

        verify(formSubmissionHelper, never()).getUserSavedFormsBulk(any(), any(), any());
    }

    @Test
    void testGetUserSavedFormsBulk_nullUserId_returns400() {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.USER_ID_KEY, null);
        requestBody.put(Constants.FORM_CONTEXT_LIST, List.of(
                Map.of("formId", "form-1", "contextId", "ctx-1")
        ));

        ApiResponse response = formService.getUserSavedFormsBulk(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.ERR_INVALID_USER_ID, response.getParams().getErrMsg());
    }

    @Test
    void testGetUserSavedFormsBulk_emptyFormContextList_returns400() {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.USER_ID_KEY, "user-123");
        requestBody.put(Constants.FORM_CONTEXT_LIST, Collections.emptyList());

        ApiResponse response = formService.getUserSavedFormsBulk(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Form context list cannot be empty", response.getParams().getErrMsg());

        verify(formSubmissionHelper, never()).getUserSavedFormsBulk(any(), any(), any());
    }

    @Test
    void testGetUserSavedFormsBulk_nullFormContextList_returns400() {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.USER_ID_KEY, "user-123");
        requestBody.put(Constants.FORM_CONTEXT_LIST, null);

        ApiResponse response = formService.getUserSavedFormsBulk(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Form context list cannot be empty", response.getParams().getErrMsg());
    }

    @Test
    void testGetUserSavedFormsBulk_repositoryException_returns500() {
        String userId = "user-123";
        List<Map<String, String>> formContextList = List.of(
                Map.of("formId", "form-1", "contextId", "ctx-1")
        );

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.USER_ID_KEY, userId);
        requestBody.put(Constants.FORM_CONTEXT_LIST, formContextList);

        when(formSubmissionHelper.getUserSavedFormsBulk(any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = formService.getUserSavedFormsBulk(requestBody);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains(Constants.ERR_INTERNAL));
    }

    @Test
    void testSearchUserFeedbackForms_success() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setPage(0);
        criteria.setSize(10);

        Map<String, Object> content = new HashMap<>();
        content.put(Constants.COUNT, 2L);
        content.put(Constants.CONTENT, List.of(
                Map.of("submissionId", "sub-1", "status", "SUBMITTED"),
                Map.of("submissionId", "sub-2", "status", "SUBMITTED")
        ));

        when(formSubmissionHelper.validateSubmissionSearch(criteria)).thenReturn(null);
        when(formSubmissionHelper.searchUserFeedbackForms(eq(criteria), any()))
                .thenReturn(content);

        ApiResponse response = formService.searchUserFeedbackForms(criteria);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.STATUS_SUCCESS, response.getParams().getStatus());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @Test
    void testSearchUserFeedbackForms_validationFailed_returns400() {
        SearchCriteria criteria = new SearchCriteria();

        when(formSubmissionHelper.validateSubmissionSearch(criteria))
                .thenReturn("formId is required");

        ApiResponse response = formService.searchUserFeedbackForms(criteria);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("formId is required", response.getParams().getErrMsg());

        verify(formSubmissionHelper, never())
                .searchUserFeedbackForms(any(), any());
    }

    @Test
    void testSearchUserFeedbackForms_emptyResult_returnsEmptyMap() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setPage(0);
        criteria.setSize(10);

        when(formSubmissionHelper.validateSubmissionSearch(criteria)).thenReturn(null);
        when(formSubmissionHelper.searchUserFeedbackForms(eq(criteria), any()))
                .thenReturn(Collections.emptyMap());

        ApiResponse response = formService.searchUserFeedbackForms(criteria);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> data = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertTrue(data.isEmpty());
    }

    @Test
    void testSearchUserFeedbackForms_repositoryException_returns500() {
        SearchCriteria criteria = new SearchCriteria();

        when(formSubmissionHelper.validateSubmissionSearch(criteria)).thenReturn(null);
        when(formSubmissionHelper.searchUserFeedbackForms(any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = formService.searchUserFeedbackForms(criteria);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains(Constants.ERR_INTERNAL));
    }

    @Test
    void testSearchUserFeedbackForms_withFilters_success() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setPage(0);
        criteria.setSize(10);
        criteria.setFilters(Map.of("formId", "form-123", "status", "SUBMITTED"));

        Map<String, Object> content = new HashMap<>();
        content.put(Constants.COUNT, 1L);
        content.put(Constants.CONTENT, List.of(
                Map.of("submissionId", "sub-1", "status", "SUBMITTED")
        ));

        when(formSubmissionHelper.validateSubmissionSearch(criteria)).thenReturn(null);
        when(formSubmissionHelper.searchUserFeedbackForms(eq(criteria), any()))
                .thenReturn(content);

        ApiResponse response = formService.searchUserFeedbackForms(criteria);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @Test
    void testSaveFeedback_success() {
        FeedbackRequest request = new FeedbackRequest();
        request.setFormId("form-123");
        String userId = "user-123";

        FormSubmission submission = new FormSubmission();
        submission.setSubmissionId("sub-123");
        submission.setFormId("form-123");
        submission.setStatus("EVALUATED");

        when(formSubmissionHelper.validateFeedback(request, userId)).thenReturn(null);
        when(formSubmissionHelper.saveFeedbackToSubmission(request, userId))
                .thenReturn(submission);

        ApiResponse response = formService.saveFeedback(request, userId);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.STATUS_SUCCESS, response.getParams().getStatus());
        Map<String, Object> data = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertEquals("sub-123", data.get(Constants.DOCUMENT_ID));
        assertEquals("form-123", data.get(Constants.FORM_ID));
        assertEquals("EVALUATED", data.get(Constants.SAVED_STATUS));
    }

    @Test
    void testSaveFeedback_validationFailed_returns400() {
        FeedbackRequest request = new FeedbackRequest();
        request.setFormId("form-123");
        String userId = "user-123";

        when(formSubmissionHelper.validateFeedback(request, userId))
                .thenReturn("Feedback is required");

        ApiResponse response = formService.saveFeedback(request, userId);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Feedback is required", response.getParams().getErrMsg());

        verify(formSubmissionHelper, never()).saveFeedbackToSubmission(any(), any());
    }

    @Test
    void testSaveFeedback_submissionNotFound_returns404() {
        FeedbackRequest request = new FeedbackRequest();
        request.setFormId("form-123");
        String userId = "user-123";

        when(formSubmissionHelper.validateFeedback(request, userId)).thenReturn(null);
        when(formSubmissionHelper.saveFeedbackToSubmission(request, userId))
                .thenReturn(null);

        ApiResponse response = formService.saveFeedback(request, userId);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg()
                .contains("No submission found for formId=form-123"));
    }

    @Test
    void testSaveFeedback_illegalStateException_returns400() {
        FeedbackRequest request = new FeedbackRequest();
        request.setFormId("form-123");
        String userId = "user-123";

        when(formSubmissionHelper.validateFeedback(request, userId)).thenReturn(null);
        when(formSubmissionHelper.saveFeedbackToSubmission(request, userId))
                .thenThrow(new IllegalStateException("Submission already evaluated"));

        ApiResponse response = formService.saveFeedback(request, userId);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Submission already evaluated", response.getParams().getErrMsg());
    }

    @Test
    void testSaveFeedback_repositoryException_returns500() {
        FeedbackRequest request = new FeedbackRequest();
        request.setFormId("form-123");
        String userId = "user-123";

        when(formSubmissionHelper.validateFeedback(request, userId)).thenReturn(null);
        when(formSubmissionHelper.saveFeedbackToSubmission(request, userId))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = formService.saveFeedback(request, userId);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg()
                .contains("Error while submitting feedback"));
    }

    @Test
    void testSubmitForm_authenticated_success() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        request.setContextId("ctx-123");
        request.setStatus("SUBMITTED");
        request.setResponses(List.of(
                QuestionResponse.builder()
                        .questionId("q1")
                        .question("Test Question")
                        .answer("Test Answer")
                        .answerType("text")
                        .build()
        ));

        String userId = "user-123";
        List<Map<String, Object>> normalizedResponses = List.of(
                Map.of("questionId", "q1", "answer", "Test Answer")
        );

        when(formSubmissionHelper.validate(eq(request), eq(userId), any(), eq(false)))
                .thenReturn(null);
        when(formSubmissionHelper.normalizeResponses(any())).thenReturn(normalizedResponses);
        when(formSubmissionHelper.saveAuthenticated(eq(request), eq(userId),
                eq("SUBMITTED"), anyString(), eq(normalizedResponses), any()))
                .thenReturn("sub-123");

        ApiResponse response = formService.submitForm(request, userId, false);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.STATUS_SUCCESS, response.getParams().getStatus());
        Map<String, Object> data = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertEquals("sub-123", data.get(Constants.DOCUMENT_ID));
        assertEquals("form-123", data.get(Constants.FORM_ID));
        assertEquals("SUBMITTED", data.get(Constants.SAVED_STATUS));
        assertEquals(1, data.get(Constants.RESPONSES_COUNT));
    }

    @Test
    void testSubmitForm_anonymous_success() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        request.setContextId("ctx-123");
        request.setStatus("SUBMITTED");
        request.setResponses(List.of(
                QuestionResponse.builder()
                        .questionId("q1")
                        .question("Test Question")
                        .answer("Test Answer")
                        .answerType("text")
                        .build()
        ));

        List<Map<String, Object>> normalizedResponses = List.of(
                Map.of("questionId", "q1", "answer", "Test Answer")
        );

        when(formSubmissionHelper.validate(eq(request), any(), any(), eq(true)))
                .thenReturn(null);
        when(formSubmissionHelper.normalizeResponses(any())).thenReturn(normalizedResponses);
        when(formSubmissionHelper.saveAnonymous(eq(request), eq("SUBMITTED"),
                anyString(), eq(normalizedResponses)))
                .thenReturn("sub-456");

        ApiResponse response = formService.submitForm(request, null, true);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> data = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertEquals("sub-456", data.get(Constants.DOCUMENT_ID));
    }

    @Test
    void testSubmitForm_validationFailed_returns400() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        String userId = "user-123";

        when(formSubmissionHelper.validate(eq(request), eq(userId), any(), eq(false)))
                .thenReturn(Constants.ERR_FORM_ID_MISSING);

        ApiResponse response = formService.submitForm(request, userId, false);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.ERR_FORM_ID_MISSING, response.getParams().getErrMsg());

        verify(formSubmissionHelper, never()).saveAuthenticated(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testSubmitForm_blankStatus_defaultsToSubmitted() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        request.setContextId("ctx-123");
        request.setStatus("");
        request.setResponses(Collections.emptyList());

        String userId = "user-123";
        List<Map<String, Object>> normalizedResponses = Collections.emptyList();

        when(formSubmissionHelper.validate(eq(request), eq(userId), any(), eq(false)))
                .thenReturn(null);
        when(formSubmissionHelper.normalizeResponses(any())).thenReturn(normalizedResponses);
        when(formSubmissionHelper.saveAuthenticated(eq(request), eq(userId),
                eq("SUBMITTED"), anyString(), eq(normalizedResponses), any()))
                .thenReturn("sub-123");

        ApiResponse response = formService.submitForm(request, userId, false);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> data = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertEquals("SUBMITTED", data.get(Constants.SAVED_STATUS));
    }

    @Test
    void testSubmitForm_nullResponses_returnsZeroCount() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        request.setContextId("ctx-123");
        request.setStatus("SUBMITTED");
        request.setResponses(null);

        String userId = "user-123";

        when(formSubmissionHelper.validate(eq(request), eq(userId), any(), eq(false)))
                .thenReturn(null);
        when(formSubmissionHelper.normalizeResponses(null)).thenReturn(Collections.emptyList());
        when(formSubmissionHelper.saveAuthenticated(any(), any(), any(), any(), any(), any()))
                .thenReturn("sub-123");

        ApiResponse response = formService.submitForm(request, userId, false);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> data = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertEquals(0, data.get(Constants.RESPONSES_COUNT));
    }

    @Test
    void testSubmitForm_duplicateSubmission_throwsDataIntegrityViolation() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        request.setContextId("ctx-123");
        request.setStatus("SUBMITTED");
        request.setResponses(Collections.emptyList());

        String userId = "user-123";

        when(formSubmissionHelper.validate(eq(request), eq(userId), any(), eq(false)))
                .thenReturn(null);
        when(formSubmissionHelper.normalizeResponses(any())).thenReturn(Collections.emptyList());
        when(formSubmissionHelper.saveAuthenticated(any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("form_submissions_pkey"));

        assertThrows(DataIntegrityViolationException.class,
                () -> formService.submitForm(request, userId, false));
    }

    @Test
    void testSubmitForm_repositoryException_returns500() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        request.setContextId("ctx-123");
        request.setStatus("SUBMITTED");
        request.setResponses(Collections.emptyList());

        String userId = "user-123";

        when(formSubmissionHelper.validate(eq(request), eq(userId), any(), eq(false)))
                .thenReturn(null);
        when(formSubmissionHelper.normalizeResponses(any())).thenReturn(Collections.emptyList());
        when(formSubmissionHelper.saveAuthenticated(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = formService.submitForm(request, userId, false);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg()
                .contains("Error while submitting form"));
    }

    @Test
    void testProcessAssignmentAnswer_submit_success() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        request.setContextId("ctx-123");
        request.setResponses(Collections.emptyList());

        String userId = "user-123";

        Forms form = new Forms();
        form.setFormId("form-123");
        form.setContextType(Constants.ASSIGNMENT);
        form.setCourseId("course-123");

        when(formSubmissionHelper.validateAnswerForAssignment(request, userId))
                .thenReturn(null);
        when(formRepository.findById("form-123")).thenReturn(Optional.of(form));
        when(formSubmissionHelper.handleFinalSubmission(request, userId, "course-123"))
                .thenReturn("sub-123");

        ApiResponse response = formService.processAssignmentAnswer(request, userId, false);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.STATUS_SUCCESS, response.getParams().getStatus());
        Map<String, Object> data = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertEquals("sub-123", data.get(Constants.DOCUMENT_ID));
        assertEquals("form-123", data.get(Constants.FORM_ID));
        assertEquals(Constants.SUBMITTED_CAPS, data.get(Constants.SAVED_STATUS));
    }

    @Test
    void testProcessAssignmentAnswer_draft_success() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        request.setContextId("ctx-123");
        request.setResponses(Collections.emptyList());

        String userId = "user-123";

        Forms form = new Forms();
        form.setFormId("form-123");
        form.setContextType(Constants.ASSIGNMENT);
        form.setCourseId("course-123");

        when(formSubmissionHelper.validateAnswerForAssignment(request, userId))
                .thenReturn(null);
        when(formRepository.findById("form-123")).thenReturn(Optional.of(form));
        when(formSubmissionHelper.handlePreviewSubmission(request, userId, "course-123"))
                .thenReturn("sub-123");

        ApiResponse response = formService.processAssignmentAnswer(request, userId, true);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> data = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertEquals(Constants.DRAFT, data.get(Constants.SAVED_STATUS));
    }

    @Test
    void testProcessAssignmentAnswer_validationFailed_returns400() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        String userId = "user-123";

        when(formSubmissionHelper.validateAnswerForAssignment(request, userId))
                .thenReturn("formId is required");

        ApiResponse response = formService.processAssignmentAnswer(request, userId, false);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("formId is required", response.getParams().getErrMsg());

        verify(formRepository, never()).findById(anyString());
    }

    @Test
    void testProcessAssignmentAnswer_formNotFound_returns400() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        String userId = "user-123";

        when(formSubmissionHelper.validateAnswerForAssignment(request, userId))
                .thenReturn(null);
        when(formRepository.findById("form-123")).thenReturn(Optional.empty());

        ApiResponse response = formService.processAssignmentAnswer(request, userId, false);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg()
                .contains("Assignment not found for formId=form-123"));
    }

    @Test
    void testProcessAssignmentAnswer_notAssignmentType_returns400() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        String userId = "user-123";

        Forms form = new Forms();
        form.setFormId("form-123");
        form.setContextType("form"); // not assignment

        when(formSubmissionHelper.validateAnswerForAssignment(request, userId))
                .thenReturn(null);
        when(formRepository.findById("form-123")).thenReturn(Optional.of(form));

        ApiResponse response = formService.processAssignmentAnswer(request, userId, false);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg()
                .contains("Form is not of type assignment"));
    }

    @Test
    void testProcessAssignmentAnswer_alreadySubmitted_returns400() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        String userId = "user-123";

        Forms form = new Forms();
        form.setFormId("form-123");
        form.setContextType(Constants.ASSIGNMENT);
        form.setCourseId("course-123");

        when(formSubmissionHelper.validateAnswerForAssignment(request, userId))
                .thenReturn(null);
        when(formRepository.findById("form-123")).thenReturn(Optional.of(form));
        when(formSubmissionHelper.handleFinalSubmission(request, userId, "course-123"))
                .thenReturn("ALREADY_SUBMITTED");

        ApiResponse response = formService.processAssignmentAnswer(request, userId, false);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Assignment already submitted. Resubmission not allowed.",
                response.getParams().getErrMsg());
    }

    @Test
    void testProcessAssignmentAnswer_alreadySubmitted_preview_returns400() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        String userId = "user-123";

        Forms form = new Forms();
        form.setFormId("form-123");
        form.setContextType(Constants.ASSIGNMENT);
        form.setCourseId("course-123");

        when(formSubmissionHelper.validateAnswerForAssignment(request, userId))
                .thenReturn(null);
        when(formRepository.findById("form-123")).thenReturn(Optional.of(form));
        when(formSubmissionHelper.handlePreviewSubmission(request, userId, "course-123"))
                .thenReturn("ALREADY_SUBMITTED");

        ApiResponse response = formService.processAssignmentAnswer(request, userId, true);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Assignment already submitted. Preview not allowed.",
                response.getParams().getErrMsg());
    }

    @Test
    void testProcessAssignmentAnswer_repositoryException_returns500() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        String userId = "user-123";

        when(formSubmissionHelper.validateAnswerForAssignment(request, userId))
                .thenReturn(null);
        when(formRepository.findById("form-123"))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = formService.processAssignmentAnswer(request, userId, false);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg()
                .contains("Error while processing assignment answer"));
    }

    @Test
    void testCreatePeerEvaluationSurveyForMDO_success() throws Exception {
        FormRequest form = new FormRequest();
        form.setTitle("MDO Peer Survey");
        form.setClientVersion(1.1f);
        form.setAdditionalProperties(new HashMap<>(Map.of(
                "identifier", "do_123",
                "thumbnail", "https://storage.googleapis.com/test.jpg",
                "duration", "3600",
                "triggerAfter", 30
        )));
        form.setFields(Collections.emptyList());

        String token = "valid-token";

        Map<String, Object> tokenData = Map.of(
                "userId", "user-123",
                "org", "org-123"
        );

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("organisation"), any(), any()))
                .thenReturn("Ministry of Testing");

        when(validatorRegistry.getValidator(Constants.PEER_VALIDATION_SURVEY))
                .thenReturn(baseFormValidator);
        when(baseFormValidator.validate(any(FormRequest.class), anyString()))
                .thenReturn(null);
        when(formConfig.getV2ClientVersion()).thenReturn(1.2);
        when(formEntityMapper.toFormsEntity(any(), anyString(), anyString(), anyString()))
                .thenReturn(mockForm);
        doNothing().when(formCreateHelper).prepareV1Form(any(Forms.class));
        when(formRepository.saveAndFlush(any(Forms.class))).thenReturn(mockForm);
        doNothing().when(peerSurveyHelper).mergeSystemGeneratedQuestions(any());

        ApiResponse response = formService.createPeerEvaluationSurveyForMDO(form, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.STATUS_SUCCESS, response.getParams().getStatus());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @Test
    void testCreatePeerEvaluationSurveyForMDO_invalidToken_returnsUnauthorized() {
        FormRequest form = new FormRequest();
        String token = "invalid-token";

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(Collections.emptyMap());

        ApiResponse response = formService.createPeerEvaluationSurveyForMDO(form, token);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals("Authentication failed: Invalid or expired access token.",
                response.getParams().getErrMsg());

        verify(formRepository, never()).saveAndFlush(any());
    }

    @Test
    void testCreatePeerEvaluationSurveyForMDO_validationFailed_returns400() throws Exception {
        FormRequest form = new FormRequest();
        form.setTitle("MDO Peer Survey");
        form.setClientVersion(1.1f);
        form.setAdditionalProperties(new HashMap<>());

        String token = "valid-token";

        Map<String, Object> tokenData = Map.of(
                "userId", "user-123",
                "org", "org-123"
        );

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("organisation"), any(), any()))
                .thenReturn("Ministry of Testing");
        when(validatorRegistry.getValidator(Constants.PEER_VALIDATION_SURVEY))
                .thenReturn(baseFormValidator);
        when(baseFormValidator.validate(any(FormRequest.class), anyString()))
                .thenReturn("additionalProperties is required");

        ApiResponse response = formService.createPeerEvaluationSurveyForMDO(form, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("additionalProperties is required", response.getParams().getErrMsg());

        verify(formRepository, never()).saveAndFlush(any());
    }

    @Test
    void testCreatePeerEvaluationSurveyForMDO_nullTokenData_returnsUnauthorized() {
        FormRequest form = new FormRequest();
        String token = "null-token";

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(null);

        ApiResponse response = formService.createPeerEvaluationSurveyForMDO(form, token);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals("Authentication failed: Invalid or expired access token.",
                response.getParams().getErrMsg());
    }

    @Test
    void testCreatePeerEvaluationSurveyForMDO_repositoryException_returns500() throws Exception {
        FormRequest form = new FormRequest();
        form.setTitle("MDO Peer Survey");
        form.setClientVersion(1.1f);
        form.setAdditionalProperties(new HashMap<>(Map.of(
                "identifier", "do_123",
                "thumbnail", "https://storage.googleapis.com/test.jpg",
                "duration", "3600",
                "triggerAfter", 30
        )));

        String token = "valid-token";

        Map<String, Object> tokenData = Map.of(
                "userId", "user-123",
                "org", "org-123"
        );

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("organisation"), any(), any()))
                .thenReturn("Ministry of Testing");
        when(validatorRegistry.getValidator(Constants.PEER_VALIDATION_SURVEY))
                .thenReturn(baseFormValidator);
        when(baseFormValidator.validate(any(FormRequest.class), anyString()))
                .thenReturn(null);
        when(formConfig.getV2ClientVersion()).thenReturn(1.2);
        when(formEntityMapper.toFormsEntity(any(), anyString(), anyString(), anyString()))
                .thenReturn(mockForm);
        doNothing().when(formCreateHelper).prepareV1Form(any(Forms.class));
        when(formRepository.saveAndFlush(any(Forms.class)))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = formService.createPeerEvaluationSurveyForMDO(form, token);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains(Constants.ERR_INTERNAL));
    }

    @Test
    void testCreatePeerEvaluationSurveyForSPV_success() throws Exception {
        FormRequest form = new FormRequest();
        form.setTitle("SPV Peer Survey");
        form.setClientVersion(1.1f);
        form.setAdditionalProperties(new HashMap<>(Map.of(
                "identifier", "do_123",
                "thumbnail", "https://storage.googleapis.com/test.jpg",
                "duration", "3600",
                "triggerAfter", 30
        )));

        CreatedFor createdFor = new CreatedFor();
        createdFor.setOrgId("org-123");
        createdFor.setOrgName("Ministry of Testing");
        form.setCreatedFor(List.of(createdFor));

        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(validatorRegistry.getValidator(Constants.PEER_VALIDATION_SURVEY))
                .thenReturn(baseFormValidator);
        when(baseFormValidator.validate(any(FormRequest.class), anyString()))
                .thenReturn(null);
        when(formConfig.getV2ClientVersion()).thenReturn(1.2);
        when(formEntityMapper.toFormsEntity(any(), anyString(), anyString(), anyString()))
                .thenReturn(mockForm);
        doNothing().when(formCreateHelper).prepareV1Form(any(Forms.class));
        when(formRepository.saveAndFlush(any(Forms.class))).thenReturn(mockForm);
        doNothing().when(peerSurveyHelper).mergeSystemGeneratedQuestions(any());

        ApiResponse response = formService.createPeerEvaluationSurveyForSPV(form, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.STATUS_SUCCESS, response.getParams().getStatus());
        assertNotNull(response.get(Constants.RESPONSE));
    }

    @Test
    void testCreatePeerEvaluationSurveyForSPV_emptyCreatedFor_returns400() {
        FormRequest form = new FormRequest();
        form.setCreatedFor(Collections.emptyList());
        String token = "valid-token";

        ApiResponse response = formService.createPeerEvaluationSurveyForSPV(form, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid request: Organization ID (createdFor) cannot be empty.",
                response.getParams().getErrMsg());

        verify(accessTokenValidator, never()).verifyUserToken(anyString());
    }

    @Test
    void testCreatePeerEvaluationSurveyForSPV_nullCreatedFor_returns400() {
        FormRequest form = new FormRequest();
        form.setCreatedFor(null);
        String token = "valid-token";

        ApiResponse response = formService.createPeerEvaluationSurveyForSPV(form, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Invalid request: Organization ID (createdFor) cannot be empty.",
                response.getParams().getErrMsg());
    }

    @Test
    void testCreatePeerEvaluationSurveyForSPV_invalidToken_returnsUnauthorized() {
        FormRequest form = new FormRequest();
        CreatedFor createdFor = new CreatedFor();
        createdFor.setOrgId("org-123");
        form.setCreatedFor(List.of(createdFor));
        String token = "invalid-token";

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(Collections.emptyMap());

        ApiResponse response = formService.createPeerEvaluationSurveyForSPV(form, token);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals("Authentication failed: Invalid or expired access token.",
                response.getParams().getErrMsg());

        verify(formRepository, never()).saveAndFlush(any());
    }

    @Test
    void testCreatePeerEvaluationSurveyForSPV_isSpvCreatedSetToTrue() throws Exception {
        FormRequest form = new FormRequest();
        form.setTitle("SPV Peer Survey");
        form.setClientVersion(1.1f);
        form.setAdditionalProperties(new HashMap<>(Map.of(
                "identifier", "do_123",
                "thumbnail", "https://storage.googleapis.com/test.jpg",
                "duration", "3600",
                "triggerAfter", 30
        )));

        CreatedFor createdFor = new CreatedFor();
        createdFor.setOrgId("org-123");
        createdFor.setOrgName("Ministry of Testing");
        form.setCreatedFor(List.of(createdFor));

        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(validatorRegistry.getValidator(Constants.PEER_VALIDATION_SURVEY))
                .thenReturn(baseFormValidator);
        when(baseFormValidator.validate(any(FormRequest.class), anyString()))
                .thenReturn(null);
        when(formConfig.getV2ClientVersion()).thenReturn(1.2);
        when(formEntityMapper.toFormsEntity(any(), anyString(), anyString(), anyString()))
                .thenReturn(mockForm);
        doNothing().when(formCreateHelper).prepareV1Form(any(Forms.class));
        when(formRepository.saveAndFlush(any(Forms.class))).thenReturn(mockForm);
        doNothing().when(peerSurveyHelper).mergeSystemGeneratedQuestions(any());

        formService.createPeerEvaluationSurveyForSPV(form, token);

        // verify isSpvCreated=true was set
        assertEquals(Boolean.TRUE, form.getAdditionalProperties().get("isSpvCreated"));
    }

    @Test
    void testCreatePeerEvaluationSurveyForSPV_validationFailed_returns400() throws Exception {
        FormRequest form = new FormRequest();
        form.setTitle("SPV Peer Survey");
        form.setClientVersion(1.1f);
        form.setAdditionalProperties(new HashMap<>());

        CreatedFor createdFor = new CreatedFor();
        createdFor.setOrgId("org-123");
        form.setCreatedFor(List.of(createdFor));

        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(validatorRegistry.getValidator(Constants.PEER_VALIDATION_SURVEY))
                .thenReturn(baseFormValidator);
        when(baseFormValidator.validate(any(FormRequest.class), anyString()))
                .thenReturn("additionalProperties is required");

        ApiResponse response = formService.createPeerEvaluationSurveyForSPV(form, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("additionalProperties is required", response.getParams().getErrMsg());

        verify(formRepository, never()).saveAndFlush(any());
    }

    @Test
    void testCreatePeerEvaluationSurveyForSPV_repositoryException_returns500() throws Exception {
        FormRequest form = new FormRequest();
        form.setTitle("SPV Peer Survey");
        form.setClientVersion(1.1f);
        form.setAdditionalProperties(new HashMap<>(Map.of(
                "identifier", "do_123",
                "thumbnail", "https://storage.googleapis.com/test.jpg",
                "duration", "3600",
                "triggerAfter", 30
        )));

        CreatedFor createdFor = new CreatedFor();
        createdFor.setOrgId("org-123");
        form.setCreatedFor(List.of(createdFor));

        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(validatorRegistry.getValidator(Constants.PEER_VALIDATION_SURVEY))
                .thenReturn(baseFormValidator);
        when(baseFormValidator.validate(any(FormRequest.class), anyString()))
                .thenReturn(null);
        when(formConfig.getV2ClientVersion()).thenReturn(1.2);
        when(formEntityMapper.toFormsEntity(any(), anyString(), anyString(), anyString()))
                .thenReturn(mockForm);
        doNothing().when(formCreateHelper).prepareV1Form(any(Forms.class));
        when(formRepository.saveAndFlush(any(Forms.class)))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = formService.createPeerEvaluationSurveyForSPV(form, token);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains(Constants.ERR_INTERNAL));
    }

    @Test
    void testUpdatePeerSurvey_success() throws Exception {
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        request.setTitle("Updated Peer Survey");
        request.setClientVersion(1.1f);
        request.setEndDate("2027-06-30T00:00:00Z");
        request.setAdditionalProperties(new HashMap<>(Map.of(
                "identifier", "do_123",
                "triggerAfter", 30,
                "completionLookBack", 60
        )));

        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms existingSurvey = new Forms();
        existingSurvey.setFormId(FORM_ID);
        existingSurvey.setStatus("Draft");
        existingSurvey.setOrgId("org-123");
        existingSurvey.setOrgName("Ministry of Testing");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(existingSurvey));
        when(peerSurveyHelper.validatePeerSurveyUpdate(any())).thenReturn(null);
        when(peerSurveyHelper.parseEndDate(anyString())).thenReturn(1814313600000L);
        when(peerSurveyHelper.validateActiveSurveyOverlap(any(), any(), anyLong(), anyLong(), any()))
                .thenReturn(null);
        when(peerSurveyHelper.validateSystemGeneratedProtection(any())).thenReturn(null);
        when(validatorRegistry.getValidator(any())).thenReturn(baseFormValidator);
        when(baseFormValidator.validateForUpdate(any(), any())).thenReturn(null);
        when(formUpdateHelper.updateFields(any(), any()))
                .thenReturn(new FieldUpdateResult(0, 0, 0, Set.of()));
        when(formRepository.saveAndFlush(any())).thenReturn(existingSurvey);

        ApiResponse response = formService.updatePeerSurvey(request, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.STATUS_SUCCESS, response.getParams().getStatus());
    }

    @Test
    void testUpdatePeerSurvey_invalidToken_returnsUnauthorized() {
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        String token = "invalid-token";

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(Collections.emptyMap());

        ApiResponse response = formService.updatePeerSurvey(request, token);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals("Invalid or expired access token", response.getParams().getErrMsg());

        verify(formRepository, never()).findById(anyString());
    }

    @Test
    void testUpdatePeerSurvey_formNotFound_returns400() {
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.empty());

        ApiResponse response = formService.updatePeerSurvey(request, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains(Constants.ERR_FORM_NOT_FOUND));
    }

    @Test
    void testUpdatePeerSurvey_unauthorizedOrg_returnsForbidden() {
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms existingSurvey = new Forms();
        existingSurvey.setFormId(FORM_ID);
        existingSurvey.setStatus("Draft");
        existingSurvey.setOrgId("different-org");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(existingSurvey));

        ApiResponse response = formService.updatePeerSurvey(request, token);

        assertEquals(HttpStatus.FORBIDDEN, response.getResponseCode());
        assertEquals("User not authorized to update this survey",
                response.getParams().getErrMsg());
    }

    @Test
    void testUpdatePeerSurvey_notDraftStatus_returns400() {
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms existingSurvey = new Forms();
        existingSurvey.setFormId(FORM_ID);
        existingSurvey.setStatus("Active"); // not Draft
        existingSurvey.setOrgId("org-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(existingSurvey));

        ApiResponse response = formService.updatePeerSurvey(request, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Survey can only be edited in Draft state",
                response.getParams().getErrMsg());
    }

    @Test
    void testUpdatePeerSurvey_validationFailed_returns400() {
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        request.setTitle("Updated Survey");
        request.setAdditionalProperties(new HashMap<>(Map.of("identifier", "do_123")));
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms existingSurvey = new Forms();
        existingSurvey.setFormId(FORM_ID);
        existingSurvey.setStatus("Draft");
        existingSurvey.setOrgId("org-123");
        existingSurvey.setOrgName("Ministry of Testing");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(existingSurvey));
        when(peerSurveyHelper.validatePeerSurveyUpdate(any()))
                .thenReturn("endDate is required");

        ApiResponse response = formService.updatePeerSurvey(request, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("endDate is required", response.getParams().getErrMsg());
    }

    @Test
    void testUpdatePeerSurvey_overlapError_returns400() {
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        request.setTitle("Updated Survey");
        request.setEndDate("2027-06-30T00:00:00Z");
        request.setAdditionalProperties(new HashMap<>(Map.of("identifier", "do_123")));
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms existingSurvey = new Forms();
        existingSurvey.setFormId(FORM_ID);
        existingSurvey.setStatus("Draft");
        existingSurvey.setOrgId("org-123");
        existingSurvey.setOrgName("Ministry of Testing");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(existingSurvey));
        when(peerSurveyHelper.validatePeerSurveyUpdate(any())).thenReturn(null);
        when(peerSurveyHelper.parseEndDate(anyString())).thenReturn(1814313600000L);
        when(peerSurveyHelper.validateActiveSurveyOverlap(any(), any(), anyLong(), anyLong(), any()))
                .thenReturn(Constants.PEER_SURVEY_ALREADY_EXISTS);

        ApiResponse response = formService.updatePeerSurvey(request, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.PEER_SURVEY_ALREADY_EXISTS, response.getParams().getErrMsg());
    }

    @Test
    void testUpdatePeerSurvey_systemGeneratedProtectionError_returns400() {
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        request.setTitle("Updated Survey");
        request.setEndDate("2027-06-30T00:00:00Z");
        request.setAdditionalProperties(new HashMap<>(Map.of("identifier", "do_123")));
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms existingSurvey = new Forms();
        existingSurvey.setFormId(FORM_ID);
        existingSurvey.setStatus("Draft");
        existingSurvey.setOrgId("org-123");
        existingSurvey.setOrgName("Ministry of Testing");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(existingSurvey));
        when(peerSurveyHelper.validatePeerSurveyUpdate(any())).thenReturn(null);
        when(peerSurveyHelper.parseEndDate(anyString())).thenReturn(1814313600000L);
        when(peerSurveyHelper.validateActiveSurveyOverlap(any(), any(), anyLong(), anyLong(), any()))
                .thenReturn(null);
        when(peerSurveyHelper.validateSystemGeneratedProtection(any()))
                .thenReturn("System generated questions cannot be removed");

        ApiResponse response = formService.updatePeerSurvey(request, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("System generated questions cannot be removed",
                response.getParams().getErrMsg());
    }

    @Test
    void testUpdatePeerSurvey_repositoryException_returns500() {
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(FORM_ID))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = formService.updatePeerSurvey(request, token);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg()
                .contains("Error while updating peer survey"));
    }

    @Test
    void testPublishPeerSurvey_success() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms survey = new Forms();
        survey.setFormId(formId);
        survey.setStatus("Draft");
        survey.setOrgId("org-123");
        survey.setEndDate(1814313600000L);
        survey.setAdditionalProperties(new HashMap<>(Map.of(
                "identifier", "do_123",
                "triggerAfter", 30
        )));

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId)).thenReturn(Optional.of(survey));
        when(peerSurveyHelper.validatePublishPeerSurvey(survey)).thenReturn(null);
        when(peerSurveyHelper.validateActiveSurveyOverlap(any(), any(), anyLong(), anyLong(), any()))
                .thenReturn(null);
        when(formRepository.saveAndFlush(any())).thenReturn(survey);

        ApiResponse response = formService.publishPeerSurvey(formId, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.STATUS_SUCCESS, response.getParams().getStatus());
        Map<String, Object> data = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertEquals(Constants.SURVEY_PUBLISHED_SUCCESSFULLY, data.get(Constants.MESSAGE));
        verify(formRepository, times(1)).saveAndFlush(any());
        verify(formEventPublisher, times(1)).publishFormUpdated(any(), any(), any());
    }

    @Test
    void testPublishPeerSurvey_invalidToken_returnsUnauthorized() {
        String formId = FORM_ID;
        String token = "invalid-token";

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(Collections.emptyMap());

        ApiResponse response = formService.publishPeerSurvey(formId, token);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals(Constants.ERR_INVALID_TOKEN, response.getParams().getErrMsg());

        verify(formRepository, never()).findById(anyString());
    }

    @Test
    void testPublishPeerSurvey_formNotFound_returns400() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId)).thenReturn(Optional.empty());

        ApiResponse response = formService.publishPeerSurvey(formId, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.ERR_FORM_NOT_FOUND, response.getParams().getErrMsg());
    }

    @Test
    void testPublishPeerSurvey_notDraftStatus_returns400() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms survey = new Forms();
        survey.setFormId(formId);
        survey.setStatus("Active"); // not Draft
        survey.setOrgId("org-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId)).thenReturn(Optional.of(survey));

        ApiResponse response = formService.publishPeerSurvey(formId, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.SURVEY_PUBLISHED_ERROR_MESSAGE,
                response.getParams().getErrMsg());
    }

    @Test
    void testPublishPeerSurvey_unauthorizedOrg_returnsForbidden() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms survey = new Forms();
        survey.setFormId(formId);
        survey.setStatus("Draft");
        survey.setOrgId("different-org");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId)).thenReturn(Optional.of(survey));

        ApiResponse response = formService.publishPeerSurvey(formId, token);

        assertEquals(HttpStatus.FORBIDDEN, response.getResponseCode());
        assertEquals(Constants.PEER_SURVEY_UNAUTHORIZED,
                response.getParams().getErrMsg());
    }

    @Test
    void testPublishPeerSurvey_validationFailed_returns400() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms survey = new Forms();
        survey.setFormId(formId);
        survey.setStatus("Draft");
        survey.setOrgId("org-123");
        survey.setAdditionalProperties(new HashMap<>(Map.of("identifier", "do_123")));

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId)).thenReturn(Optional.of(survey));
        when(peerSurveyHelper.validatePublishPeerSurvey(survey))
                .thenReturn("End date missing");

        ApiResponse response = formService.publishPeerSurvey(formId, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("End date missing", response.getParams().getErrMsg());

        verify(formRepository, never()).saveAndFlush(any());
    }

    @Test
    void testPublishPeerSurvey_overlapError_returnsConflict() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms survey = new Forms();
        survey.setFormId(formId);
        survey.setStatus("Draft");
        survey.setOrgId("org-123");
        survey.setEndDate(1814313600000L);
        survey.setAdditionalProperties(new HashMap<>(Map.of("identifier", "do_123")));

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId)).thenReturn(Optional.of(survey));
        when(peerSurveyHelper.validatePublishPeerSurvey(survey)).thenReturn(null);
        when(peerSurveyHelper.validateActiveSurveyOverlap(any(), any(), anyLong(), anyLong(), any()))
                .thenReturn(Constants.PEER_SURVEY_ALREADY_EXISTS);

        ApiResponse response = formService.publishPeerSurvey(formId, token);

        assertEquals(HttpStatus.CONFLICT, response.getResponseCode());
        assertEquals(Constants.SURVEY_EXITS, response.getParams().getErrMsg());

        verify(formRepository, never()).saveAndFlush(any());
    }

    @Test
    void testPublishPeerSurvey_repositoryException_returns500() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = formService.publishPeerSurvey(formId, token);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg()
                .contains("Error while publishing survey"));
    }

    @Test
    void testEndPeerSurvey_success() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms survey = new Forms();
        survey.setFormId(formId);
        survey.setStatus("Active");
        survey.setOrgId("org-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId)).thenReturn(Optional.of(survey));
        when(formRepository.saveAndFlush(any())).thenReturn(survey);

        ApiResponse response = formService.endPeerSurvey(formId, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.STATUS_SUCCESS, response.getParams().getStatus());
        Map<String, Object> data = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertEquals(Constants.SURVEY_ENDED_SUCCESSFULLY, data.get(Constants.MESSAGE));

        verify(formRepository, times(1)).saveAndFlush(any());
        verify(formEventPublisher, times(1)).publishFormUpdated(any(), any(), any());
    }

    @Test
    void testEndPeerSurvey_invalidToken_returnsUnauthorized() {
        String formId = FORM_ID;
        String token = "invalid-token";

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(Collections.emptyMap());

        ApiResponse response = formService.endPeerSurvey(formId, token);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals(Constants.ERR_INVALID_TOKEN, response.getParams().getErrMsg());

        verify(formRepository, never()).findById(anyString());
    }

    @Test
    void testEndPeerSurvey_formNotFound_returns400() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId)).thenReturn(Optional.empty());

        ApiResponse response = formService.endPeerSurvey(formId, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.ERR_FORM_NOT_FOUND, response.getParams().getErrMsg());
    }

    @Test
    void testEndPeerSurvey_unauthorizedOrg_returnsForbidden() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms survey = new Forms();
        survey.setFormId(formId);
        survey.setStatus("Active");
        survey.setOrgId("different-org");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId)).thenReturn(Optional.of(survey));

        ApiResponse response = formService.endPeerSurvey(formId, token);

        assertEquals(HttpStatus.FORBIDDEN, response.getResponseCode());
        assertEquals(Constants.ERR_UNAUTHORIZED_ARCHIVE,
                response.getParams().getErrMsg());
    }

    @Test
    void testEndPeerSurvey_notActiveStatus_returns400() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms survey = new Forms();
        survey.setFormId(formId);
        survey.setStatus("Draft"); // not Active
        survey.setOrgId("org-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId)).thenReturn(Optional.of(survey));

        ApiResponse response = formService.endPeerSurvey(formId, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.ERR_END_INVALID_STATUS,
                response.getParams().getErrMsg());
    }

    @Test
    void testEndPeerSurvey_setsEndedStatusAndEndDate() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms survey = new Forms();
        survey.setFormId(formId);
        survey.setStatus("Active");
        survey.setOrgId("org-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId)).thenReturn(Optional.of(survey));
        when(formRepository.saveAndFlush(any())).thenReturn(survey);

        formService.endPeerSurvey(formId, token);

        assertEquals("Ended", survey.getStatus());
        assertNotNull(survey.getEndDate());
        assertNotNull(survey.getUpdatedAt());
    }

    @Test
    void testEndPeerSurvey_repositoryException_returns500() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = formService.endPeerSurvey(formId, token);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg()
                .contains("Error while ending survey"));
    }

    @Test
    void testArchivePeerSurvey_success() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms survey = new Forms();
        survey.setFormId(formId);
        survey.setStatus("Ended");
        survey.setOrgId("org-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId)).thenReturn(Optional.of(survey));
        when(formRepository.saveAndFlush(any())).thenReturn(survey);

        ApiResponse response = formService.archivePeerSurvey(formId, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.STATUS_SUCCESS, response.getParams().getStatus());
        Map<String, Object> data = (Map<String, Object>) response.get(Constants.RESPONSE);
        assertEquals(Constants.SURVEY_ARCHIVED, data.get(Constants.MESSAGE));

        verify(formRepository, times(1)).saveAndFlush(any());
        verify(formEventPublisher, times(1)).publishFormUpdated(any(), any(), any());
    }

    @Test
    void testArchivePeerSurvey_invalidToken_returnsUnauthorized() {
        String formId = FORM_ID;
        String token = "invalid-token";

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(Collections.emptyMap());

        ApiResponse response = formService.archivePeerSurvey(formId, token);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals(Constants.ERR_INVALID_TOKEN, response.getParams().getErrMsg());

        verify(formRepository, never()).findById(anyString());
    }

    @Test
    void testArchivePeerSurvey_formNotFound_returns400() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId)).thenReturn(Optional.empty());

        ApiResponse response = formService.archivePeerSurvey(formId, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.ERR_FORM_NOT_FOUND, response.getParams().getErrMsg());
    }

    @Test
    void testArchivePeerSurvey_unauthorizedOrg_returnsForbidden() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms survey = new Forms();
        survey.setFormId(formId);
        survey.setStatus("Ended");
        survey.setOrgId("different-org");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId)).thenReturn(Optional.of(survey));

        ApiResponse response = formService.archivePeerSurvey(formId, token);

        assertEquals(HttpStatus.FORBIDDEN, response.getResponseCode());
        assertEquals(Constants.ERR_UNAUTHORIZED_ARCHIVE,
                response.getParams().getErrMsg());
    }

    @Test
    void testArchivePeerSurvey_notEndedStatus_returns400() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms survey = new Forms();
        survey.setFormId(formId);
        survey.setStatus("Active"); // not Ended
        survey.setOrgId("org-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId)).thenReturn(Optional.of(survey));

        ApiResponse response = formService.archivePeerSurvey(formId, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.ERR_ARCHIVE_INVALID_STATUS,
                response.getParams().getErrMsg());
    }

    @Test
    void testArchivePeerSurvey_setsArchivedStatusAndDate() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        Forms survey = new Forms();
        survey.setFormId(formId);
        survey.setStatus("Ended");
        survey.setOrgId("org-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId)).thenReturn(Optional.of(survey));
        when(formRepository.saveAndFlush(any())).thenReturn(survey);

        formService.archivePeerSurvey(formId, token);

        assertEquals("Archived", survey.getStatus());
        assertNotNull(survey.getArchivedDate());
        assertNotNull(survey.getUpdatedAt());
    }

    @Test
    void testArchivePeerSurvey_repositoryException_returns500() {
        String formId = FORM_ID;
        String token = "valid-token";
        Map<String, Object> tokenData = Map.of("userId", "user-123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(tokenData);
        when(peerSurveyHelper.fetchRecordsFromDB(anyString(), eq("user"), any(), any()))
                .thenReturn("org-123");
        when(formRepository.findById(formId))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = formService.archivePeerSurvey(formId, token);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg()
                .contains("Error while archiving survey"));
    }

    @Test
    void testSubmitPeerValidationSurvey_learnerSubmission_success() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        request.setContextId("ctx-123");
        request.setStatus("SUBMITTED");
        request.setActionType(null); // not REVIEW

        String token = "valid-token";

        ApiResponse expectedResponse = new ApiResponse(Constants.API_PEER_SURVEY_SUBMIT);
        expectedResponse.put(Constants.RESPONSE, Map.of(
                Constants.DOCUMENT_ID, "sub-123",
                Constants.FORM_ID, "form-123"
        ));

        when(formSubmissionHelper.handleLearnerSubmission(request, token, false))
                .thenReturn(expectedResponse);

        ApiResponse response = formService.submitPeerValidationSurvey(request, token, false);

        assertNotNull(response);
        verify(formSubmissionHelper, times(1))
                .handleLearnerSubmission(request, token, false);
        verify(formSubmissionHelper, never())
                .handlePeerReview(any(), anyString());
    }

    @Test
    void testSubmitPeerValidationSurvey_peerReview_success() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setSubmissionId("sub-123");
        request.setActionType("REVIEW");
        request.setReviewStatus("ACCEPTED");

        String token = "valid-token";

        ApiResponse expectedResponse = new ApiResponse(Constants.API_PEER_REVIEW);
        expectedResponse.put(Constants.RESPONSE, Map.of(
                Constants.DOCUMENT_ID, "sub-123"
        ));

        when(formSubmissionHelper.handlePeerReview(request, token))
                .thenReturn(expectedResponse);

        ApiResponse response = formService.submitPeerValidationSurvey(request, token, false);

        assertNotNull(response);
        verify(formSubmissionHelper, times(1))
                .handlePeerReview(request, token);
        verify(formSubmissionHelper, never())
                .handleLearnerSubmission(any(), anyString(), anyBoolean());
    }

    @Test
    void testSubmitPeerValidationSurvey_reviewActionTypeCaseInsensitive() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setSubmissionId("sub-123");
        request.setActionType("review"); // lowercase
        request.setReviewStatus("REJECTED");

        String token = "valid-token";

        ApiResponse expectedResponse = new ApiResponse(Constants.API_PEER_REVIEW);

        when(formSubmissionHelper.handlePeerReview(request, token))
                .thenReturn(expectedResponse);

        ApiResponse response = formService.submitPeerValidationSurvey(request, token, false);

        assertNotNull(response);
        verify(formSubmissionHelper, times(1))
                .handlePeerReview(request, token);
        verify(formSubmissionHelper, never())
                .handleLearnerSubmission(any(), anyString(), anyBoolean());
    }

    @Test
    void testSubmitPeerValidationSurvey_anonymousUser_learnerSubmission() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        request.setActionType(null);

        String token = "valid-token";

        ApiResponse expectedResponse = new ApiResponse(Constants.API_PEER_SURVEY_SUBMIT);

        when(formSubmissionHelper.handleLearnerSubmission(request, token, true))
                .thenReturn(expectedResponse);

        ApiResponse response = formService.submitPeerValidationSurvey(request, token, true);

        assertNotNull(response);
        verify(formSubmissionHelper, times(1))
                .handleLearnerSubmission(request, token, true);
    }

    @Test
    void testSubmitPeerValidationSurvey_emptyActionType_routesToLearner() {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-123");
        request.setActionType(""); // empty — not REVIEW

        String token = "valid-token";

        ApiResponse expectedResponse = new ApiResponse(Constants.API_PEER_SURVEY_SUBMIT);

        when(formSubmissionHelper.handleLearnerSubmission(request, token, false))
                .thenReturn(expectedResponse);

        ApiResponse response = formService.submitPeerValidationSurvey(request, token, false);

        assertNotNull(response);
        verify(formSubmissionHelper, times(1))
                .handleLearnerSubmission(request, token, false);
        verify(formSubmissionHelper, never())
                .handlePeerReview(any(), anyString());
    }
}

