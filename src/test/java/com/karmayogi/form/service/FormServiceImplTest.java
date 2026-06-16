package com.karmayogi.form.service;

import com.karmayogi.form.entity.FormQuestions;
import com.karmayogi.form.entity.Forms;
import com.karmayogi.form.repository.FormQuestionsRepository;
import com.karmayogi.form.repository.FormRepository;
import com.karmayogi.form.utils.ApiResponse;
import com.karmayogi.form.utils.Constants;
import com.karmayogi.form.utils.FormCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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


}

