package com.rnvo.notifier.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.rnvo.notifier.model.EventPayload;
import com.rnvo.notifier.model.WebhookConfig;
import com.rnvo.notifier.service.DlqService;
import com.rnvo.notifier.service.HttpDispatcher;
import com.rnvo.notifier.service.WebhookConfigService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.common.errors.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Kafka com.rnvo.notifier.consumer that orchestrates the end-to-end webhook delivery pipeline:
 * com.rnvo.notifier.config lookup → fairness check → HTTP dispatch → DLQ on failure.
 */
@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final WebhookConfigService configService;
    private final DlqService dlqService;
    private final HttpDispatcher httpDispatcher;
    private final ObjectMapper objectMapper;
    private final Executor virtualThreadExecutor;
    private final MeterRegistry meterRegistry;
    private final Counter retryCounter;

    public EventConsumer(WebhookConfigService configService,
                         DlqService dlqService,
                         HttpDispatcher httpDispatcher,
                         ObjectMapper objectMapper,
                         @Qualifier("virtualThreadExecutor") Executor virtualThreadExecutor,
                         MeterRegistry meterRegistry
    ) {
        this.configService = configService;
        this.dlqService = dlqService;
        this.objectMapper = objectMapper;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.httpDispatcher = httpDispatcher;
        this.meterRegistry = meterRegistry;
        this.retryCounter = Counter.builder("webhook.retry.rate")
                .description("Number of times an event was sent for retry")
                .register(meterRegistry);
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 60000, multiplier = 5.0, maxDelay = 1500000), // 1,5,25mins
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_DELAY_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            exclude = {InvalidFormatException.class, SerializationException.class, DeserializationException.class}
    )
    @KafkaListener(topics = "events", properties = {"max.poll.records:500"}, groupId = "webhook-workers-${HOSTNAME}")
    public void onEvent(EventPayload event, Acknowledgment ack, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.debug("Received event: account={}, type={} topic={}", event.getAccountId(), event.getEventType(), topic);
        Timer.Sample ackTimerSample = Timer.start(meterRegistry);

        CompletableFuture.runAsync(() -> {
            Timer.Sample processingSample = Timer.start(meterRegistry);
            try {
                Optional<WebhookConfig> config = configService.getConfig(event.getAccountId(), event.getEventType());
                if (config.isEmpty() || !config.get().getIsActive()) {
                    log.debug("No active config for account={}, event={}. Skipping.", event.getAccountId(), event.getEventType());

                    ackTimerSample.stop(meterRegistry.timer("webhook.ack.latency"));
                    processingSample.stop(meterRegistry.timer("webhook.worker.processing", "outcome", "skipped"));
                    return;
                }

                String payload = objectMapper.writeValueAsString(event);
                httpDispatcher.dispatch(config.get().getTargetUrl(), payload);
                log.info("Event {} from topic {} processed successfully", event.getEventId(), topic);

                ackTimerSample.stop(meterRegistry.timer("webhook.ack.latency"));
                processingSample.stop(meterRegistry.timer("webhook.worker.processing", "outcome", "success"));
            } catch (InvalidFormatException | SerializationException | DeserializationException e) {
                log.error("Failed to de/serialize event payload: {}", e.getLocalizedMessage());

                dlqService.save(event, "Serialization error: " + e.getLocalizedMessage());

                ackTimerSample.stop(meterRegistry.timer("webhook.ack.latency"));
                processingSample.stop(meterRegistry.timer("webhook.worker.processing", "outcome", "serialization_error"));
            } catch (Exception e) {
                log.error("Unexpected error processing event {} from topic {}: {}", event.getEventId(), topic, e.getLocalizedMessage());

                retryCounter.increment();
                ackTimerSample.stop(meterRegistry.timer("webhook.ack.latency"));
                processingSample.stop(meterRegistry.timer("webhook.worker.processing", "outcome", "failure"));
                throw new RuntimeException("Unexpected error processing event: " + e.getLocalizedMessage());
            } finally {
                ack.acknowledge();
            }
        }, virtualThreadExecutor);
    }

    @DltHandler
    public void handleFailedEvents(EventPayload event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Event {} failed all retries, sending failure event to DB from topic {}", event.getEventId(), topic);
        dlqService.save(event, "Event processing failed " + event.getEventId());
    }
}
