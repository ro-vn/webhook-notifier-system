package com.rnvo.notifier.config;

import com.rnvo.notifier.model.EventPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {KafkaConfig.class, VirtualThreadConfig.class})
@ActiveProfiles("test")
class KafkaConfigTest {

    @Autowired
    ConcurrentKafkaListenerContainerFactory<String, EventPayload> factory;

    @Test
    void concurrentKafkaListenerContainerFactoryIsConfiguredWithVirtualThreads() {
        assertThat(factory).isNotNull();
        // Since we explicitly configure a SimpleAsyncTaskExecutor with virtual threads enabled
        assertThat(factory.getContainerProperties().getListenerTaskExecutor()).isInstanceOf(SimpleAsyncTaskExecutor.class);
    }
}
