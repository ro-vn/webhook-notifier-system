package com.rnvo.notifier.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.rnvo.notifier.model.EventPayload;
import com.rnvo.notifier.model.WebhookConfig;
import com.rnvo.notifier.service.DlqService;
import com.rnvo.notifier.service.FairnessEnforcer;
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
import org.springframework.kafka.core.KafkaTemplate;
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
 * Consumer for the low-priority lane ('events-low').
 * Enforces fairness even in the low lane to prevent a single whale from starvation of other 'small' whale accounts.
 */
@Component
public class LowLaneConsumer {

    private static final Logger log = LoggerFactory.getLogger(LowLaneConsumer.class);

    private final WebhookConfigService configService;
    private final DlqService dlqService;
    private final HttpDispatcher httpDispatcher;
    private final ObjectMapper objectMapper;
    private final Executor virtualThreadExecutor;
    private final MeterRegistry meterRegistry;
    private final FairnessEnforcer fairnessEnforcer;
    private final KafkaTemplate<String, EventPayload> kafkaTemplate;
    private final Counter retryCounter;
    private final Counter fairnessThrottledCounter;
    private final Counter fairnessDivertedCounter;

    public LowLaneConsumer(WebhookConfigService configService,
                           DlqService dlqService,
                           HttpDispatcher httpDispatcher,
                           ObjectMapper objectMapper,
                           @Qualifier("virtualThreadExecutor") Executor virtualThreadExecutor,
                           MeterRegistry meterRegistry,
                           FairnessEnforcer fairnessEnforcer,
                           KafkaTemplate<String, EventPayload> kafkaTemplate
    ) {
        this.configService = configService;
        this.dlqService = dlqService;
        this.objectMapper = objectMapper;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.httpDispatcher = httpDispatcher;
        this.meterRegistry = meterRegistry;
        this.fairnessEnforcer = fairnessEnforcer;
        this.kafkaTemplate = kafkaTemplate;

        this.retryCounter = Counter.builder("webhook.lowlane.retry.rate")
                .description("Number of times an event in low lane was sent for retry")
                .register(meterRegistry);
        this.fairnessThrottledCounter = Counter.builder("webhook.lowlane.fairness.throttled")
                .description("Number of events throttled in low lane due to fairness")
                .register(meterRegistry);
        this.fairnessDivertedCounter = Counter.builder("webhook.fairness.diverted")
                .description("Number of events diverted to low lane due to fairness")
                .register(meterRegistry);
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 6000, multiplier = 5.0, maxDelay = 1500000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_DELAY_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            exclude = {InvalidFormatException.class, SerializationException.class, DeserializationException.class}
    )
    @KafkaListener(topics = "events-low", properties = {"max.poll.records:100"}, groupId = "webhook-lowlane-workers-${HOSTNAME}")
    public void onEvent(EventPayload event, Acknowledgment ack, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.debug("Received low-lane event: account={}, type={} topic={}", event.getAccountId(), event.getEventType(), topic);
        Timer.Sample ackTimerSample = Timer.start(meterRegistry);

        // Event consumer processing logic could be reused
        CompletableFuture.runAsync(() -> {
            Timer.Sample processingSample = Timer.start(meterRegistry);
            try {
                if (!fairnessEnforcer.isAllowed(event.getAccountId())) {
                    log.warn("Account {} still exceeding limit in low lane. Diverting event {} from topic {} to low lane", event.getAccountId(), event.getEventId(), topic);
                    kafkaTemplate.send("events-low", event.getAccountId(), event);

                    fairnessDivertedCounter.increment();
                    processingSample.stop(meterRegistry.timer("webhook.lowlane.processing", "outcome", "diverted"));
                    fairnessThrottledCounter.increment();
                    throw new RuntimeException("Fairness limit exceeded in low lane for account: " + event.getAccountId());
                }

                Optional<WebhookConfig> config = configService.getConfig(event.getAccountId(), event.getEventType());
                if (config.isEmpty() || !config.get().getIsActive()) {
                    log.debug("No active config for account={}, event={}. Skipping.", event.getAccountId(), event.getEventType());
                    ackTimerSample.stop(meterRegistry.timer("webhook.lowlane.ack.latency"));
                    processingSample.stop(meterRegistry.timer("webhook.lowlane.worker.processing", "outcome", "skipped"));
                    return;
                }

                String payload = objectMapper.writeValueAsString(event);
                httpDispatcher.dispatch(config.get().getTargetUrl(), payload);
                log.info("Low-lane event {} from topic {} processed successfully", event.getEventId(), topic);

                ackTimerSample.stop(meterRegistry.timer("webhook.lowlane.ack.latency"));
                processingSample.stop(meterRegistry.timer("webhook.lowlane.worker.processing", "outcome", "success"));
            } catch (InvalidFormatException | SerializationException | DeserializationException e) {
                log.error("Failed to de/serialize low-lane event payload: {}", e.getLocalizedMessage());
                dlqService.save(event, "Serialization error in low-lane: " + e.getLocalizedMessage());
                ackTimerSample.stop(meterRegistry.timer("webhook.lowlane.ack.latency"));
                processingSample.stop(meterRegistry.timer("webhook.lowlane.worker.processing", "outcome", "serialization_error"));
            } catch (Exception e) {
                log.error("Error processing low-lane event {}: {}", event.getEventId(), e.getLocalizedMessage());
                retryCounter.increment();
                ackTimerSample.stop(meterRegistry.timer("webhook.lowlane.ack.latency"));
                processingSample.stop(meterRegistry.timer("webhook.lowlane.worker.processing", "outcome", "failure"));
                throw new RuntimeException("Low-lane processing failed: " + e.getLocalizedMessage());
            } finally {
                ack.acknowledge();
            }
        }, virtualThreadExecutor);
    }

    @DltHandler
    public void handleFailedEvents(EventPayload event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Low-lane event {} failed all retries, sending failure event to DB from topic {}", event.getEventId(), topic);
        dlqService.save(event, "Low-lane event processing failed " + event.getEventId());
    }
}
