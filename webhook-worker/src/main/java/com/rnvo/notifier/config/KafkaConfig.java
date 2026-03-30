package com.rnvo.notifier.config;

import com.rnvo.notifier.model.EventPayload;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

import static org.apache.kafka.clients.consumer.ConsumerConfig.*;

/**
 * Kafka consumer configuration.
 * - Manual acknowledgment (At-Least-Once delivery guarantee)
 * - JSON deserialization for EventPayload
 * - Virtual-thread executor for listener method dispatch
 * - Concurrency tuned to match Kafka topic partition count
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.listener.concurrency:2}")
    private int listenerConcurrency;

    @Bean
    public ConsumerFactory<String, EventPayload> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(MAX_POLL_INTERVAL_MS_CONFIG, 300000); // 5 minutes
        props.put(SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        JsonDeserializer<EventPayload> deserializer = new JsonDeserializer<>(EventPayload.class);
        deserializer.setRemoveTypeHeaders(true);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(false);

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventPayload> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, EventPayload> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(listenerConcurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setPollTimeout(100);

        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("vt-kafka-");
        executor.setVirtualThreads(true);
        factory.getContainerProperties().setListenerTaskExecutor(executor);

        return factory;
    }
}
