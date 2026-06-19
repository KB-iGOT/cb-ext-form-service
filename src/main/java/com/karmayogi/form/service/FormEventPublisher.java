package com.karmayogi.form.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.karmayogi.form.entity.FormQuestions;
import com.karmayogi.form.entity.Forms;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author anil
 *
 */
@Component
public class FormEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FormEventPublisher.class);

    private final KafkaTemplate<String, String> formKafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String formEventsTopic;

    public FormEventPublisher(KafkaTemplate<String, String> formKafkaTemplate,
                              ObjectMapper objectMapper,
                              @Value("${form.kafka.topic.form-events}") String formEventsTopic) {
        this.formKafkaTemplate = formKafkaTemplate;
        this.objectMapper = objectMapper;
        this.formEventsTopic = formEventsTopic;
    }

    public void publishFormCreated(Forms form, List<FormQuestions> v1Questions) {
        try {
            ObjectNode node = objectMapper.valueToTree(form);

            boolean isV1 = form.getVersion() != null && form.getVersion() == 1;
            if (isV1 && CollectionUtils.isNotEmpty(v1Questions)) {
                node.set("fields", objectMapper.valueToTree(v1Questions));
            }

            String json = objectMapper.writeValueAsString(node);
            formKafkaTemplate.send(formEventsTopic, form.getFormId(), json);
            log.info("FormEventPublisher published formId={} topic={}",
                    form.getFormId(), formEventsTopic);
        } catch (Exception e) {
            log.error("FormEventPublisher failed to publish formId={}: {}",
                    form.getFormId(), e.getMessage(), e);
            throw new RuntimeException("Kafka publish failed for formId=" + form.getFormId(), e);
        }
    }
}
