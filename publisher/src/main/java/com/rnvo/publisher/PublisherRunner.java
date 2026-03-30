package com.rnvo.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates upstream systems by publishing events to Kafka in an open-loop manner.
 * Supports "peak capacity" testing and "whale account" fairness scenarios.
 */
@Component
public class PublisherRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PublisherRunner.class);
    private static final String TOPIC = "events";

    @Value("${PUBLISHER_RATE_PER_SECONDS:10}")
    private int publishRatePerSec;

    @Value("${PUBLISHER_WHALE_ENABLED:false}")
    private boolean whaleEnabled;

    @Value("${WHALE_ACCOUNT_ID:whale_account_1}")
    private String whaleAccountId;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicLong smallAccountCounter = new AtomicLong(0);
    private final AtomicLong whaleAccountCounter = new AtomicLong(0);

    public PublisherRunner(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        log.info("Starting Open-Loop Publisher: Rate={} msgs/sec, WhaleMode={}", publishRatePerSec, whaleEnabled);

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        long delayMicros = 1_000_000L / publishRatePerSec;
        executor.scheduleAtFixedRate(this::publishNext, 0, Math.max(1, delayMicros), TimeUnit.MICROSECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdown));

    }

    private void publishNext() {
        try {
            if (whaleEnabled) {
                // In Whale mode, we mostly publish for the whale but trickle some small accounts
                // Let's say 90% whale, 10% small accounts
                if (Math.random() < 0.9) {
                    long seq = whaleAccountCounter.getAndIncrement();
                    String payload = buildPayload(whaleAccountId, "subscriber.created", (int) seq);
                    kafkaTemplate.send(TOPIC, whaleAccountId, payload);
                } else {
                    String accountId = "small_account_" + (smallAccountCounter.get() % 100);
                    long seq = smallAccountCounter.getAndIncrement();
                    String payload = buildPayload(accountId, "subscriber.created", (int) seq);
                    kafkaTemplate.send(TOPIC, accountId, payload);
                }
            } else {
                // Balanced load across many accounts
                String accountId = "small_account_" + (smallAccountCounter.get() % 100);
                long seq = smallAccountCounter.getAndIncrement();
                String payload = buildPayload(accountId, "subscriber.created", (int) seq);
                kafkaTemplate.send(TOPIC, accountId, payload);
            }

            long total = whaleAccountCounter.get() + smallAccountCounter.get();
            if (total % 1000 == 0) {
                log.info("Total published: Whale={}, SmallAccounts={}", whaleAccountCounter.get(), smallAccountCounter.get());
            }
        } catch (Exception e) {
            log.error("Failed to publish event: {}", e.getMessage());
        }
    }

    private String buildPayload(String accountId, String eventType, int sequence) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", "ev_%d".formatted(sequence));
        payload.put("accountId", accountId);
        payload.put("eventType", eventType);
        payload.put("timestamp", System.currentTimeMillis());

        Map<String, Object> data = new HashMap<>();
        data.put("subscriberEmail", "user" + sequence + "@example.com");
        data.put("subscriberName", "User " + sequence);
        data.put("sequence", sequence);
        payload.put("data", data);

        return objectMapper.writeValueAsString(payload);
    }
}
