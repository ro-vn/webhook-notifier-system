package com.rnvo.receiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates an integration partner's webhook receiver.
 * Returns 200 OK on success, or configurable 503 errors to test retry logic.
 */
@RestController
public class ReceiverController {

    private static final Logger log = LoggerFactory.getLogger(ReceiverController.class);

    @Value("${FAILURE_RATE:0.1}")
    private double failureRate;

    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);

    @PostMapping("/api/webhook")
    public ResponseEntity<String> receive(@RequestBody String payload) {
        if (Math.random() < failureRate) {
            long failures = failureCount.incrementAndGet();
            log.warn("Simulating 503 failure (total failures: {})", failures);
            return ResponseEntity.status(503).body("Service Unavailable");
        }

        long successes = successCount.incrementAndGet();
        if (successes % 1000 == 0) {
            log.info("Received {} webhooks successfully", successes);
        }
        log.debug("Received webhook: {}", payload);
        return ResponseEntity.ok("OK");
    }
}
