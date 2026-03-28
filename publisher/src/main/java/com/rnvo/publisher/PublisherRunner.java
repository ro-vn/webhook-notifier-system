package com.rnvo.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Simulates upstream systems by publishing events to Kafka.
 * Includes a "whale" burst mode that fires 100,000 events for a single account
 * interspersed with events from small accounts — used to stress-test fairness.
 */
@Component
public class PublisherRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PublisherRunner.class);
    private static final String TOPIC = "events";
    private static final int WHALE_BURST_SIZE = 100_000;
    private static final int SMALL_ACCOUNT_INTERVAL = 100; // every Nth whale event

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PublisherRunner(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting event publication — {} whale events + small account events",
                WHALE_BURST_SIZE);

        int smallAccountCount = 0;

        for (int i = 0; i < WHALE_BURST_SIZE; i++) {
            // Whale event
            String whalePayload = buildPayload("whale_account_1", "subscriber.created", i);
            kafkaTemplate.send(TOPIC, "whale_account_1", whalePayload);

            // Intersperse small account events
            if (i % SMALL_ACCOUNT_INTERVAL == 0) {
                String smallPayload = buildPayload("small_account_1", "subscriber.created",
                        smallAccountCount++);
                kafkaTemplate.send(TOPIC, "small_account_1", smallPayload);
            }

            if (i % 10_000 == 0) {
                log.info("Published {} whale events, {} small account events", i, smallAccountCount);
            }
        }

        log.info("Publication complete: {} whale events, {} small account events",
                WHALE_BURST_SIZE, smallAccountCount);
    }

    private String buildPayload(String accountId, String eventType, int sequence) throws Exception {
        Map<String, Object> payload = new HashMap<>();
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
