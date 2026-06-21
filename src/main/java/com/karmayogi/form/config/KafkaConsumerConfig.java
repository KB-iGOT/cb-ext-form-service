package com.karmayogi.form.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.MicrometerConsumerListener;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * @author anil
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${form.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${form.kafka.consumer.group-id}")
    private String groupId;

    @Value("${form.kafka.consumer.auto-offset-reset}")
    private String autoOffsetReset;

    @Value("${form.kafka.consumer.max-poll-records}")
    private int maxPollRecords;

    @Value("${form.kafka.consumer.concurrency}")
    private int concurrency;

    @Bean
    public ConsumerFactory<String, String> formConsumerFactory(MeterRegistry meterRegistry) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        DefaultKafkaConsumerFactory<String, String> factory =
                new DefaultKafkaConsumerFactory<>(props);
        factory.addListener(new MicrometerConsumerListener<>(meterRegistry));

        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> formKafkaListenerContainerFactory(
            ConsumerFactory<String, String> formConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(formConsumerFactory);
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        return factory;
    }

}

