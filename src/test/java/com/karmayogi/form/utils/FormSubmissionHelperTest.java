package com.karmayogi.form.utils;

import com.karmayogi.form.entity.FormSubmission;
import com.karmayogi.form.model.FormSubmissionRequest;
import com.karmayogi.form.repository.FormSubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author anil
 */
@ExtendWith(MockitoExtension.class)
public class FormSubmissionHelperTest {

    @Mock
    private FormSubmissionRepository formSubmissionRepository;

    @Mock
    private FormCacheService formCacheService;

    @InjectMocks
    private FormSubmissionHelper formSubmissionHelper;

    @Test
    void bulkStatusUsesLowercaseSubmittedKey() {
        when(formCacheService.getSubmissionStatus(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(formSubmissionRepository.findSubmittedByUserAndFormIds("user-1", List.of("form-1", "form-2")))
                .thenReturn(List.<Object[]>of(new Object[]{"form-1", "ctx-1"}));

        List<Map<String, Object>> result = formSubmissionHelper.getUserSavedFormsBulk("user-1",
                List.of(Map.of("formId", "form-1", "contextId", "ctx-1"),
                        Map.of("formId", "form-2", "contextId", "ctx-2")),
                formCacheService);

        assertEquals(Map.of("formId", "form-1", "contextId", "ctx-1", "submitted", true), result.get(0));
        assertEquals(Map.of("formId", "form-2", "contextId", "ctx-2", "submitted", false), result.get(1));
    }

    @Test
    void saveAuthenticatedCreatesNewSubmission() {
        FormSubmissionRequest request = request("prog-1");
        when(formSubmissionRepository.findByFormIdAndUserIdAndContextId("form-1", "user-1", "prog-1"))
                .thenReturn(Optional.empty());

        String submissionId = formSubmissionHelper.saveAuthenticated(request, "user-1", "SUBMITTED",
                "form", List.of(Map.of("questionId", "q1", "answer", "a1")), Map.of("fullName", "Learner"));

        FormSubmission saved = savedSubmission();
        assertEquals(submissionId, saved.getSubmissionId());
        assertEquals("prog-1", saved.getContextId());
        assertEquals("SUBMITTED", saved.getStatus());
        assertNull(saved.getUpdatedBy());
        verify(formCacheService).invalidateBulkStatus("user-1", "form-1", "prog-1");
    }

    @Test
    void saveAuthenticatedOverwritesExistingSubmission() {
        FormSubmission existing = new FormSubmission();
        existing.setSubmissionId("sub-old");
        existing.setFormId("form-1");
        existing.setUserId("user-1");
        existing.setContextId("prog-1");
        existing.setStatus("SUBMITTED");
        existing.setSubmittedDate(1L);
        existing.setResponses(List.of(Map.of("questionId", "q1", "answer", "old")));
        when(formSubmissionRepository.findByFormIdAndUserIdAndContextId("form-1", "user-1", "prog-1"))
                .thenReturn(Optional.of(existing));

        String submissionId = formSubmissionHelper.saveAuthenticated(request("prog-1"), "user-1", "SUBMITTED",
                "form", List.of(Map.of("questionId", "q1", "answer", "new")), Map.of());

        FormSubmission saved = savedSubmission();
        assertEquals("sub-old", submissionId);
        assertEquals("sub-old", saved.getSubmissionId());
        assertEquals(List.of(Map.of("questionId", "q1", "answer", "new")), saved.getResponses());
        assertEquals("user-1", saved.getUpdatedBy());
        assertNotNull(saved.getUpdatedDate());
        assertTrue(saved.getSubmittedDate() > 1L);
    }

    @Test
    void saveAuthenticatedStoresBlankContextIdAsEmpty() {
        when(formSubmissionRepository.findByFormIdAndUserIdAndContextId("form-1", "user-1", ""))
                .thenReturn(Optional.empty());

        formSubmissionHelper.saveAuthenticated(request(null), "user-1", "DRAFT", "form", List.of(), Map.of());

        assertEquals("", savedSubmission().getContextId());
    }

    private FormSubmissionRequest request(String contextId) {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setFormId("form-1");
        request.setContextId(contextId);
        return request;
    }

    private FormSubmission savedSubmission() {
        ArgumentCaptor<FormSubmission> captor = ArgumentCaptor.forClass(FormSubmission.class);
        verify(formSubmissionRepository).save(captor.capture());
        return captor.getValue();
    }
}
