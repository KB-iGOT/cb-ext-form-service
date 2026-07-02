package com.karmayogi.form.controller;

import com.karmayogi.form.service.FormService;
import com.karmayogi.form.utils.ApiResponse;
import com.karmayogi.form.utils.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author anil
 */
@ExtendWith(MockitoExtension.class)
public class FormControllerTest {

    @Mock
    FormService formService;

    @InjectMocks
    FormController formController;

    MockMvc mockMvc;

    private static final String FORM_ID = "1773913383431";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(formController).build();
    }

    @Test
    void testGetFormById() throws Exception {
        ApiResponse mockResponse = new ApiResponse(Constants.API_GET_FORM_BY_ID);

        when(formService.getFormById(FORM_ID)).thenReturn(mockResponse);

        mockMvc.perform(get("/forms/read")
                        .param("formId", FORM_ID))
                .andExpect(status().isOk());

        verify(formService).getFormById(FORM_ID);
    }

    @Test
    void testGetFormById_missingFormId_returns400() throws Exception {
        mockMvc.perform(get("/forms/read"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateForm_success() throws Exception {
        ApiResponse mockResponse = new ApiResponse(Constants.API_CREATE_FORM);

        when(formService.createForm(any(), eq("user-123"))).thenReturn(mockResponse);

        String body = "{\"title\":\"My Form\",\"status\":\"PUBLISH\",\"contextType\":\"form\"}";

        mockMvc.perform(post("/forms/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-authenticated-userid", "user-123")
                        .content(body))
                .andExpect(status().isOk());

        verify(formService).createForm(any(), eq("user-123"));
    }

    @Test
    void testCreateForm_missingUserHeader_returns400() throws Exception {
        String body = "{\"title\":\"My Form\",\"status\":\"PUBLISH\",\"contextType\":\"form\"}";

        mockMvc.perform(post("/forms/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}

