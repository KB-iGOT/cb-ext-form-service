package com.karmayogi.form.service;

import com.karmayogi.form.entity.FormQuestions;
import com.karmayogi.form.entity.Forms;
import com.karmayogi.form.repository.FormQuestionsRepository;
import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.model.FieldUpdateResult;
import com.karmayogi.form.utils.*;
import com.karmayogi.form.validator.ValidatorRegistry;
import com.karmayogi.form.validator.BaseFormValidator;
import com.karmayogi.form.model.FormRequest;
import com.karmayogi.form.model.FieldMeta;
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
    void testUpdateForm_success_allFieldsChanged() throws Exception {
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
        when(baseFormValidator.validate(any(FormRequest.class), anyString())).thenReturn(null);
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
        when(baseFormValidator.validate(any(FormRequest.class), anyString()))
                .thenReturn(validationError);

        ApiResponse response = formService.updateForm(request, "user-1");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.STATUS_FAILED, response.getParams().getStatus());
        assertEquals(validationError, response.getParams().getErrMsg());

        verify(formUpdateHelper, never()).updateFields(any(), any());
        verify(formRepository, never()).saveAndFlush(any());
    }

    @Test
    void testUpdateForm_noFieldChanges() throws Exception {
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        request.setTitle("Updated Form");
        request.setFields(Collections.emptyList());

        FieldUpdateResult fieldResult = new FieldUpdateResult(0, 0, 0, Set.of());

        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(mockForm));
        when(validatorRegistry.getValidator("form")).thenReturn(baseFormValidator);
        when(baseFormValidator.validate(any(FormRequest.class), anyString())).thenReturn(null);
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
    void testUpdateForm_dataIntegrityViolation_throwsException() throws Exception {
        FormRequest request = new FormRequest();
        request.setFormId(FORM_ID);
        request.setTitle("Updated Form");

        FieldUpdateResult fieldResult = new FieldUpdateResult(0, 1, 0, Set.of());

        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(mockForm));
        when(validatorRegistry.getValidator("form")).thenReturn(baseFormValidator);
        when(baseFormValidator.validate(any(FormRequest.class), anyString())).thenReturn(null);
        when(formUpdateHelper.updateFields(any(Forms.class), any(FormRequest.class))).thenReturn(fieldResult);
        when(formRepository.saveAndFlush(any(Forms.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violated"));

        assertThrows(DataIntegrityViolationException.class, () ->
                formService.updateForm(request, "user-1"));
    }

    @Test
    void testUpdateForm_multipleFieldsUpdate_success() throws Exception {
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
        when(baseFormValidator.validate(any(FormRequest.class), anyString())).thenReturn(null);
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

}

