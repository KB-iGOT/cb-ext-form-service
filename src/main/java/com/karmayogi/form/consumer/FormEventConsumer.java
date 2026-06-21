package com.karmayogi.form.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * @author anil
 */
@Component
public class FormEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(FormEventConsumer.class);

    private final FormEsIndexer esIndexer;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public FormEventConsumer(FormEsIndexer esIndexer,
                             ObjectMapper objectMapper,
                             MeterRegistry meterRegistry) {
        this.esIndexer = esIndexer;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = "${form.kafka.topic.form-events}",
            groupId = "${form.kafka.consumer.group-id}",
            containerFactory = "formKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String formId = null;
        try {
            meterRegistry.counter("form.kafka.consumer.received").increment();

            String json = record.value();
            if (StringUtils.isBlank(json)) {
                log.warn("FormEventConsumer received blank message partition={} offset={}",
                        record.partition(), record.offset());
                ack.acknowledge();
                return;
            }

            formId = StringUtils.isNotBlank(record.key())
                    ? record.key()
                    : extractFormId(json);

            if (StringUtils.isBlank(formId)) {
                log.error("FormEventConsumer could not resolve formId partition={} offset={} json={}",
                        record.partition(), record.offset(), json);
                ack.acknowledge();
                return;
            }

            log.info("FormEventConsumer received formId={} partition={} offset={}",
                    formId, record.partition(), record.offset());

            esIndexer.index(formId, json);

            ack.acknowledge();
            meterRegistry.counter("form.kafka.consumer.ack").increment();
            log.info("FormEventConsumer acked formId={}", formId);

        } catch (Exception e) {
            meterRegistry.counter("form.kafka.consumer.nack").increment();
            log.error("FormEventConsumer failed to index formId={}: {}", formId, e.getMessage(), e);
        }
    }


    private String extractFormId(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode idNode = node.get("formId");
            return (idNode != null && !idNode.isNull()) ? idNode.asText() : null;
        } catch (Exception e) {
            log.warn("FormEventConsumer failed to parse formId from json: {}", e.getMessage());
            return null;
        }
    }


}
