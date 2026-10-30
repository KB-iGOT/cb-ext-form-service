package com.karmayogi.form.service;

import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.utils.FormEventPublisher;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author anil
 */
@ExtendWith(MockitoExtension.class)
public class NpsSubmissionServiceTest {

    private static final String NPS_ID = "1724749263292";
    private static final String UPGRADED_ID = "1700000000000";
    private static final String NPS_TOPIC = "qa.nps_submission";
    private static final String UPGRADED_TOPIC = "qa.nps_upgraded_submission";

    @Mock
    private RestHighLevelClient client;

    @Mock
    private FormEventPublisher formEventPublisher;

    @Mock
    private FormConfig formConfig;

    @InjectMocks
    private NpsSubmissionService npsSubmissionService;

    @BeforeEach
    void setUp() {
        lenient().when(formConfig.getNpsFormId()).thenReturn(NPS_ID);
        lenient().when(formConfig.getNpsFormSecondaryId()).thenReturn("");
        lenient().when(formConfig.getNpsFormUpgradedId()).thenReturn(UPGRADED_ID);
        lenient().when(formConfig.getNpsTopic()).thenReturn(NPS_TOPIC);
        lenient().when(formConfig.getNpsUpgradedTopic()).thenReturn(UPGRADED_TOPIC);
        lenient().when(formConfig.getNpsDataIndex()).thenReturn("fs-forms-data");
        lenient().when(formConfig.getNpsActivityLogIndex()).thenReturn("fs-forms-activity-log");
    }

    @Test
    void recognisesConfiguredNpsForms() {
        assertTrue(npsSubmissionService.isNpsForm(NPS_ID));
        assertTrue(npsSubmissionService.isNpsForm(UPGRADED_ID));
        assertFalse(npsSubmissionService.isNpsForm("1682565333894"));
        assertFalse(npsSubmissionService.isNpsForm(""));
    }

    @Test
    void ratingIsSavedLoggedAndPublished() throws IOException {
        Map<String, Object> dataObject = new LinkedHashMap<>();
        dataObject.put("Please rate your experience with the platform", 7);
        dataObject.put("Tell us more about your experience", "Automation");
        dataObject.put("Empty answer", "");

        ResponseEntity<Object> response = npsSubmissionService.submit(body(NPS_ID, dataObject, 1793320316260L), "f:igot:user-1");

        assertEquals(Map.of("statusInfo", Map.of("statusCode", 200, "statusMessage", "success", "errorMessage", ""),
                "responseData", true), response.getBody());

        List<IndexRequest> requests = indexRequests(2);
        Map<String, Object> doc = requests.get(0).sourceAsMap();
        assertEquals("fs-forms-data", requests.get(0).index());
        assertEquals(Long.parseLong(NPS_ID), doc.get("formId"));
        assertEquals(requests.get(0).id(), doc.get("applicationId"));
        assertEquals(15, requests.get(0).id().length());
        assertEquals(1, doc.get("version"));
        assertEquals(1793320316260L, doc.get("timestamp"));
        assertEquals(Map.of("Please rate your experience with the platform", 7,
                "Tell us more about your experience", "Automation"), doc.get("dataObject"));
        assertEquals("NEW", doc.get("status"));
        assertEquals("user-1", doc.get("createdBy"));
        assertEquals("user-1", doc.get("updatedBy"));
        assertNotNull(doc.get("createdDate"));

        Map<String, Object> activity = requests.get(1).sourceAsMap();
        assertEquals("fs-forms-activity-log", requests.get(1).index());
        assertEquals(requests.get(0).id(), activity.get("id"));
        assertEquals("Application", activity.get("type"));
        assertEquals(doc, activity.get("updatedObject"));

        Map<String, Object> event = event(NPS_TOPIC);
        assertEquals("user-1", event.get("userID"));
        assertEquals(7, event.get("rating"));
        assertEquals(1793320316260L, event.get("timestamp"));
        assertEquals(true, event.get("submitted"));
        assertEquals("nps_submission", event.get("activityDesc"));
        assertEquals(Long.parseLong(NPS_ID), event.get("activityID"));
        assertEquals("formsService", event.get("originPlatform"));
        assertFalse(event.containsKey("userType"));
        verify(formEventPublisher, never()).pushToPipeline(any(), eq(UPGRADED_TOPIC), any());
    }

    @Test
    void closedPopupWithMinusOneIsSavedAndPublishedAsRated() throws IOException {
        npsSubmissionService.submit(body(NPS_ID, Map.of("Please rate your experience with the platform", -1), 1L), "user-1");

        indexRequests(2);
        Map<String, Object> event = event(NPS_TOPIC);
        assertEquals(-1, event.get("rating"));
        assertEquals(true, event.get("submitted"));
    }

    @Test
    void upgradedFormPublishesUserType() throws IOException {
        assertUpgraded(9, "Promoters", true);
        assertUpgraded(7, "Passives", true);
        assertUpgraded(3, "Detracters", true);
        assertUpgraded(-1, "Detracters", false);
    }

    @Test
    void discardedPopupOnlyPublishesNotSubmitted() throws IOException {
        ResponseEntity<Object> response = npsSubmissionService.submit(body(UPGRADED_ID, Map.of(), 1793320316260L), "user-1");

        assertEquals("Data object is missing", ((Map<?, ?>) response.getBody()).get("responseData"));
        verify(client, never()).index(any(IndexRequest.class), any(RequestOptions.class));
        Map<String, Object> event = event(UPGRADED_TOPIC);
        assertNull(event.get("rating"));
        assertEquals(false, event.get("submitted"));
        assertEquals("Detracters", event.get("userType"));
    }

    @Test
    void microsecondTimestampIsReducedForTheEventOnly() throws IOException {
        npsSubmissionService.submit(body(NPS_ID, Map.of("Please rate your experience with the platform", 8),
                1793320316260123L), "user-1");

        assertEquals(1793320316260123L, indexRequests(2).get(0).sourceAsMap().get("timestamp"));
        assertEquals(1793320316260L, event(NPS_TOPIC).get("timestamp"));
    }

    @Test
    void saveFailureReturnsProcessFailedAndPublishesNothing() throws IOException {
        when(client.index(any(IndexRequest.class), any(RequestOptions.class))).thenThrow(new IOException("es down"));

        ResponseEntity<Object> response = npsSubmissionService.submit(
                body(NPS_ID, Map.of("Please rate your experience with the platform", 8), 1L), "user-1");

        assertEquals(Map.of("statusCode", 320, "statusMessage", "Process failed, Please try again.",
                "errorMessage", "Process failed, Please try again."), response.getBody());
        verifyNoInteractions(formEventPublisher);
    }

    private void assertUpgraded(int rating, String userType, boolean submitted) throws IOException {
        reset(formEventPublisher);
        npsSubmissionService.submit(body(UPGRADED_ID, Map.of("Rate the platform", rating), 1L), "user-1");
        Map<String, Object> event = event(UPGRADED_TOPIC);
        assertEquals(rating, event.get("rating"));
        assertEquals(userType, event.get("userType"));
        assertEquals(submitted, event.get("submitted"));
        verify(formEventPublisher, never()).pushToPipeline(any(), eq(NPS_TOPIC), any());
    }

    private Map<String, Object> body(String formId, Map<String, Object> dataObject, long timestamp) {
        Map<String, Object> body = new HashMap<>();
        body.put("formId", Long.parseLong(formId));
        body.put("timestamp", timestamp);
        body.put("version", 1);
        body.put("dataObject", dataObject);
        return body;
    }

    private List<IndexRequest> indexRequests(int expected) throws IOException {
        ArgumentCaptor<IndexRequest> captor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(client, times(expected)).index(captor.capture(), any(RequestOptions.class));
        return captor.getAllValues();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> event(String topic) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(formEventPublisher).pushToPipeline(captor.capture(), eq(topic), eq(topic));
        return (Map<String, Object>) captor.getValue();
    }
}
