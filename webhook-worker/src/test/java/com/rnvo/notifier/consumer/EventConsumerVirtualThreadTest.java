package com.rnvo.notifier.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rnvo.notifier.model.EventPayload;
import com.rnvo.notifier.model.WebhookConfig;
import com.rnvo.notifier.service.DlqService;
import com.rnvo.notifier.service.FairnessEnforcer;
import com.rnvo.notifier.service.HttpDispatcher;
import com.rnvo.notifier.service.WebhookConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventConsumerVirtualThreadTest {

    @Mock private WebhookConfigService configService;
    @Mock private FairnessEnforcer fairnessEnforcer;
    @Mock private HttpDispatcher httpDispatcher;
    @Mock private DlqService dlqService;
    @Mock private KafkaTemplate<String, EventPayload> kafkaTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private Acknowledgment acknowledgment;

    private EventConsumer eventConsumer;

    @BeforeEach
    void setUp() {
        eventConsumer = new EventConsumer(
                configService,
                fairnessEnforcer,
                httpDispatcher,
                dlqService,
                kafkaTemplate,
                objectMapper,
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    @Test
    void onEventExecutesOffloadedToVirtualThread() throws InterruptedException, JsonProcessingException {
        EventPayload payload = new EventPayload();
        payload.setAccountId("acc-1");
        payload.setEventType("type-1");

        WebhookConfig config = new WebhookConfig();
        config.setIsActive(true);
        config.setTargetUrl("http://example.com");

        when(configService.getConfig("acc-1", "type-1")).thenReturn(Optional.of(config));
        when(fairnessEnforcer.isAllowed("acc-1")).thenReturn(true);
        when(objectMapper.writeValueAsString(payload)).thenReturn("{}");

        CountDownLatch dispatcherLatch = new CountDownLatch(1);
        AtomicBoolean isVirtualThread = new AtomicBoolean(false);

        when(httpDispatcher.dispatch(anyString(), anyString())).thenAnswer(invocation -> {
            isVirtualThread.set(Thread.currentThread().isVirtual());
            dispatcherLatch.countDown();
            return true;
        });

        eventConsumer.onEvent(payload, acknowledgment);

        boolean completed = dispatcherLatch.await(2, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        assertThat(isVirtualThread.get()).as("Dispatch should happen on a virtual thread").isTrue();
        // Since ack.acknowledge() might be called asynchronously, wait for it
        verify(acknowledgment, timeout(1000).times(1)).acknowledge();
    }
}
