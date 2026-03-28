package com.rnvo.notifier.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rnvo.notifier.model.EventPayload;
import com.rnvo.notifier.model.WebhookConfig;
import com.rnvo.notifier.service.DlqService;
import com.rnvo.notifier.service.FairnessEnforcer;
import com.rnvo.notifier.service.HttpDispatcher;
import com.rnvo.notifier.service.WebhookConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
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
    private final FairnessEnforcer fairnessEnforcer;
    private final HttpDispatcher httpDispatcher;
    private final DlqService dlqService;
    private final KafkaTemplate<String, EventPayload> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Executor virtualThreadExecutor;

    public EventConsumer(WebhookConfigService configService,
                         FairnessEnforcer fairnessEnforcer,
                         HttpDispatcher httpDispatcher,
                         DlqService dlqService,
                         KafkaTemplate<String, EventPayload> kafkaTemplate,
                         ObjectMapper objectMapper,
                         @Qualifier("virtualThreadExecutor") Executor virtualThreadExecutor) {
        this.configService = configService;
        this.fairnessEnforcer = fairnessEnforcer;
        this.httpDispatcher = httpDispatcher;
        this.dlqService = dlqService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @KafkaListener(topics = "events", groupId = "webhook-workers")
    public void onEvent(EventPayload event, Acknowledgment ack) {
        log.debug("Received event: account={}, type={}", event.getAccountId(), event.getEventType());

        CompletableFuture.runAsync(() -> {
            try {
                // 1. Config lookup
                Optional<WebhookConfig> config = configService.getConfig(
                        event.getAccountId(), event.getEventType());

                if (config.isEmpty() || !config.get().getIsActive()) {
                    log.debug("No active config for account={}, event={}. Skipping.",
                            event.getAccountId(), event.getEventType());
                    return;
                }

                // 2. Fairness check
                if (!fairnessEnforcer.isAllowed(event.getAccountId())) {
                    log.info("Rate limit hit for account={}. Pushing to retry topic.",
                            event.getAccountId());
                    kafkaTemplate.send("events.retry", event.getAccountId(), event);
                    return;
                }

                // 3. HTTP dispatch
                String payload = objectMapper.writeValueAsString(event);
                boolean success = httpDispatcher.dispatch(config.get().getTargetUrl(), payload);

                // 4. DLQ on failure
                if (!success) {
                    dlqService.save(event, "All retries exhausted");
                }

            } catch (JsonProcessingException e) {
                log.error("Failed to serialize event payload: {}", e.getMessage());
                dlqService.save(event, "Serialization error: " + e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error processing event: {}", e.getMessage(), e);
                dlqService.save(event, "Unexpected error: " + e.getMessage());
            } finally {
                ack.acknowledge();
            }
        }, virtualThreadExecutor);
    }
}
