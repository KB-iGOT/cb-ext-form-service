package com.karmayogi.form.consumer;

import com.karmayogi.form.utils.SurveyMetricsService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * @author anil
 */
@Component
@RequiredArgsConstructor
public class PeerSurveyNotificationSentConsumer {

    private static final Logger log = LoggerFactory.getLogger(PeerSurveyNotificationSentConsumer.class);

    private final SurveyMetricsService surveyMetricsService;

    @KafkaListener(topics = "${peer.survey.notification.sent.topic}", groupId = "${peer.survey.notification.read.group}")
    public void processNotificationSentMessage(ConsumerRecord<String, String> data) {
        log.info("PeerSurveyNotificationSentConsumer received message key={}",
                data.key());
        try {
            if (StringUtils.isNotBlank(data.value())) {
                CompletableFuture.runAsync(() -> {
                    try {
                        surveyMetricsService.incrementTotalNotificationCount(data.value());
                    } catch (Exception e) {
                        log.error("Error processing notification sent: {}", e.getMessage(), e);
                    }
                });
            } else {
                log.error("Invalid Kafka message in PeerSurveyNotificationSentConsumer");
            }
        } catch (Exception e) {
            log.error("PeerSurveyNotificationSentConsumer error: {}", e.getMessage(), e);
        }
    }

}
