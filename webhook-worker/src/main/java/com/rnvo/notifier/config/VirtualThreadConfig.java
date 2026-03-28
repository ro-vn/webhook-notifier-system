package com.rnvo.notifier.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Provides a shared virtual-thread executor for:
 *   - Kafka listener task dispatch (freeing the poller thread)
 *   - HttpClient internal scheduling
 *   - CompletableFuture.runAsync() in EventConsumer
 *
 * newVirtualThreadPerTaskExecutor() creates a new virtual thread per
 * submitted task — no pool sizing needed, no queue backpressure.
 */
@Configuration
@EnableAsync
public class VirtualThreadConfig {

    @Bean("virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
