package com.rnvo.notifier.service;

import com.rnvo.notifier.repository.DlqRecordRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DlqMetricsService {

    private static final Logger log = LoggerFactory.getLogger(DlqMetricsService.class);

    private final DlqRecordRepository dlqRepository;
    private final MeterRegistry meterRegistry;

    private final AtomicLong dlqSize = new AtomicLong(0);
    private final AtomicLong oldestDlqAgeSeconds = new AtomicLong(0);

    public DlqMetricsService(DlqRecordRepository dlqRepository, MeterRegistry meterRegistry) {
        this.dlqRepository = dlqRepository;
        this.meterRegistry = meterRegistry;

        Gauge.builder("webhook.dlq.size", dlqSize, AtomicLong::get)
                .description("Total number of records in the DLQ")
                .register(meterRegistry);

        Gauge.builder("webhook.dlq.age.seconds", oldestDlqAgeSeconds, AtomicLong::get)
                .description("Age of the oldest DLQ record in seconds")
                .register(meterRegistry);
    }

    @Scheduled(fixedRate = 15000)
    public void updateDlqMetrics() {
        try {
            long currentCount = dlqRepository.count();
            this.dlqSize.set(currentCount);

            dlqRepository.findTopByOrderByFailedAtAsc().ifPresentOrElse(
                    oldest -> {
                        long ageSeconds = Duration.between(oldest.getFailedAt(), LocalDateTime.now()).getSeconds();
                        this.oldestDlqAgeSeconds.set(Math.max(ageSeconds, 0));
                    },
                    () -> this.oldestDlqAgeSeconds.set(0)
            );

        } catch (Exception e) {
            log.error("Failed to update DLQ metrics: {}", e.getMessage());
        }
    }
}
