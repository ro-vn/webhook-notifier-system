package com.rnvo.notifier.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.rnvo.notifier.model.WebhookConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configures the L1 in-process Caffeine cache for webhook configs.
 * 1-minute TTL, max 10,000 entries.
 */
@Configuration
public class CaffeineConfig {

    @Bean
    public Cache<String, WebhookConfig> webhookConfigCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build();
    }
}
