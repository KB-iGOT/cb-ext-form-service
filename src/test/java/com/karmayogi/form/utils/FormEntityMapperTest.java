package com.karmayogi.form.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmayogi.form.entity.Forms;
import com.karmayogi.form.model.FormRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * @author anil
 */
public class FormEntityMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FormEntityMapper formEntityMapper =
            new FormEntityMapper(objectMapper, mock(PeerSurveyHelper.class));

    @Test
    void updateKeepsClientVersionExact() throws Exception {
        FormRequest request = objectMapper.readValue(
                "{\"title\":\"T\",\"clientVersion\":1.1}", FormRequest.class);
        Forms form = new Forms();

        formEntityMapper.applyUpdateFields(form, request, "user-1");

        // was 1.100000023841858 while FormRequest.clientVersion was a Float
        assertEquals(1.1, form.getClientVersion());
    }

    @Test
    void updateKeepsV2ClientVersionExact() throws Exception {
        FormRequest request = objectMapper.readValue(
                "{\"title\":\"T\",\"clientVersion\":1.2}", FormRequest.class);
        Forms form = new Forms();

        formEntityMapper.applyUpdateFields(form, request, "user-1");

        assertEquals(Constants.VERSION_2, form.getClientVersion());
    }
}
