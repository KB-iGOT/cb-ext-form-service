package com.karmayogi.form.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.entity.FormQuestions;
import com.karmayogi.form.entity.Forms;
import com.karmayogi.form.model.FormSubmissionRequest;
import com.karmayogi.form.model.QuestionResponse;
import com.karmayogi.form.repository.FormQuestionsRepository;
import com.karmayogi.form.repository.FormRepository;
import com.karmayogi.form.utils.ApiResponse;
import com.karmayogi.form.utils.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author anil
 */
@ExtendWith(MockitoExtension.class)
public class LegacyFormSubmitServiceTest {

    private static final String FORM_ID = "1682565333894";
    private static final String NPS_FORM_ID = "1724749263292";

    @Mock
    private FormService formService;

    @Mock
    private FormRepository formRepository;

    @Mock
    private FormQuestionsRepository formQuestionsRepository;

    @Mock
    private FormConfig formConfig;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private LegacyFormSubmitService legacyFormSubmitService;

    @BeforeEach
    void setUp() {
        lenient().when(formConfig.getNonQuestionFieldTypes()).thenReturn(Set.of("heading", "separator"));
        lenient().when(formConfig.getNpsFormIds()).thenReturn(Set.of(NPS_FORM_ID));
        lenient().when(formConfig.getLegacyFormServiceUrl()).thenReturn("http://form-service:8099");
    }

    @Test
    void convertsDataObjectToResponsesAndReturnsLegacySuccess() {
        Forms form = new Forms();
        form.setFormId(FORM_ID);
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(form));
        when(formQuestionsRepository.findByFormIdOrderByQuestionOrderAsc(FORM_ID)).thenReturn(List.of(
                question("q-heading", "Section A", "heading"),
                question("q1", "Rate your experience with course ", "rating"),
                question("q2", "What are the skills that ypu prefer for upcoming course ", "checkbox"),
                question("q3", "Please add your feedback ", "textarea")));
        when(formService.submitForm(any(), eq("user-1"), eq(false))).thenReturn(apiResponse(Constants.STATUS_SUCCESS_LOWERCASE, null));

        ResponseEntity<Object> response = legacyFormSubmitService.submit(legacyBody(), "user-1", null);

        ArgumentCaptor<FormSubmissionRequest> captor = ArgumentCaptor.forClass(FormSubmissionRequest.class);
        verify(formService).submitForm(captor.capture(), eq("user-1"), eq(false));
        FormSubmissionRequest request = captor.getValue();
        assertEquals(FORM_ID, request.getFormId());
        assertEquals("do_114289052704030720197", request.getContextId());
        assertEquals("Cloud Security with survey form", request.getContextName());
        assertEquals(Constants.SUBMITTED_CAPS, request.getStatus());
        assertEquals(1, request.getVersion());

        List<QuestionResponse> responses = request.getResponses();
        assertEquals(3, responses.size());
        assertEquals("q1", responses.get(0).getQuestionId());
        assertEquals(5, responses.get(0).getAnswer());
        assertEquals("rating", responses.get(0).getAnswerType());
        assertEquals(List.of("Excel"), responses.get(1).getAnswer());
        assertEquals("", responses.get(2).getAnswer());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of(
                "statusInfo", Map.of("statusCode", 200, "statusMessage", "success", "errorMessage", ""),
                "responseData", true), response.getBody());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void usesJsonbQuestionsForV2Forms() {
        Forms form = new Forms();
        form.setFormId(FORM_ID);
        form.setQuestions(List.of(
                Map.of("id", "q1", "name", "Rate your experience with course ", "fieldType", "rating"),
                Map.of("id", "q-sep", "name", "sep", "fieldType", "separator")));
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(form));
        when(formService.submitForm(any(), any(), eq(false))).thenReturn(apiResponse(Constants.STATUS_SUCCESS_LOWERCASE, null));

        legacyFormSubmitService.submit(legacyBody(), "user-1", null);

        ArgumentCaptor<FormSubmissionRequest> captor = ArgumentCaptor.forClass(FormSubmissionRequest.class);
        verify(formService).submitForm(captor.capture(), any(), eq(false));
        assertEquals(1, captor.getValue().getResponses().size());
        assertEquals("q1", captor.getValue().getResponses().get(0).getQuestionId());
        verifyNoInteractions(formQuestionsRepository);
    }

    @Test
    void npsFormIsForwardedToOldFormService() {
        Map<String, Object> body = new HashMap<>(Map.of("formId", Long.parseLong(NPS_FORM_ID),
                "dataObject", Map.of("Please rate your experience with the platform", 7)));
        when(restTemplate.exchange(eq("http://form-service:8099/forms/v1/saveFormSubmit"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"responseData\":true}"));

        ResponseEntity<Object> response = legacyFormSubmitService.submit(body, "user-1", "{\"emailId\":\"x\"}");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("{\"responseData\":true}", response.getBody());
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), entity.capture(), eq(String.class));
        assertEquals("user-1", entity.getValue().getHeaders().getFirst(Constants.X_AUTHENTICATED_USER_ID));
        assertEquals("{\"emailId\":\"x\"}", entity.getValue().getHeaders().getFirst(Constants.X_USER_INFO));
        assertSame(body, entity.getValue().getBody());
        verifyNoInteractions(formService, formRepository);
    }

    @Test
    void missingFormIdReturnsLegacyFailure() {
        ResponseEntity<Object> response = legacyFormSubmitService.submit(new HashMap<>(), "user-1", null);

        assertEquals(failure("Invalid form id"), response.getBody());
    }

    @Test
    void emptyDataObjectReturnsLegacyFailure() {
        ResponseEntity<Object> response = legacyFormSubmitService.submit(
                new HashMap<>(Map.of("formId", Long.parseLong(FORM_ID))), "user-1", null);

        assertEquals(failure("Form data object is empty"), response.getBody());
        verifyNoInteractions(formService);
    }

    @Test
    void unknownFormReturnsLegacyFailure() {
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.empty());

        ResponseEntity<Object> response = legacyFormSubmitService.submit(legacyBody(), "user-1", null);

        assertEquals(failure("Form is not available"), response.getBody());
        verifyNoInteractions(formService);
    }

    @Test
    void submitErrorIsReturnedAsLegacyFailure() {
        Forms form = new Forms();
        form.setFormId(FORM_ID);
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(form));
        when(formQuestionsRepository.findByFormIdOrderByQuestionOrderAsc(FORM_ID))
                .thenReturn(List.of(question("q1", "Rate your experience with course ", "rating")));
        when(formService.submitForm(any(), any(), eq(false)))
                .thenReturn(apiResponse(Constants.STATUS_FAILED, Constants.ERR_INVALID_USER_ID));

        ResponseEntity<Object> response = legacyFormSubmitService.submit(legacyBody(), null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(failure(Constants.ERR_INVALID_USER_ID), response.getBody());
    }

    private Map<String, Object> legacyBody() {
        Map<String, Object> dataObject = new LinkedHashMap<>();
        dataObject.put("Rate your experience with course ", 5);
        dataObject.put("What are the skills that ypu prefer for upcoming course ", List.of("Excel"));
        dataObject.put("Course ID and Name", "do_114289052704030720197,Cloud Security with survey form");
        Map<String, Object> body = new HashMap<>();
        body.put("formId", Long.parseLong(FORM_ID));
        body.put("timestamp", 1793341319382L);
        body.put("version", 1);
        body.put("dataObject", dataObject);
        return body;
    }

    private FormQuestions question(String id, String name, String fieldType) {
        FormQuestions question = new FormQuestions();
        question.setQuestionId(id);
        question.setFormId(FORM_ID);
        question.setName(name);
        question.setFieldType(fieldType);
        return question;
    }

    private ApiResponse apiResponse(String status, String errMsg) {
        ApiResponse response = new ApiResponse(Constants.API_SUBMIT_FORM);
        response.getParams().setStatus(status);
        response.getParams().setErrMsg(errMsg);
        return response;
    }

    private Map<String, Object> failure(String errorMessage) {
        return Map.of("statusInfo", Map.of("statusCode", 320,
                "statusMessage", "Process failed, Please try again.", "errorMessage", errorMessage));
    }
}
