package com.rnvo.notifier.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.rnvo.notifier.model.WebhookConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.rnvo.notifier.repository.WebhookConfigRepository;

import java.time.Duration;
import java.util.Optional;

/**
 * Multi-tier cache-aside lookup for webhook configurations.
 * L1 (Caffeine) → L2 (Redis) → L3 (PostgreSQL).
 */
@Service
public class WebhookConfigService {

    private static final Logger log = LoggerFactory.getLogger(WebhookConfigService.class);
    private static final String REDIS_PREFIX = "webhook:";
    private static final Duration REDIS_TTL = Duration.ofMinutes(5);

    private final Cache<String, WebhookConfig> caffeineCache;
    private final RedisTemplate<String, Object> redisTemplate;
    private final WebhookConfigRepository repository;

    public WebhookConfigService(Cache<String, WebhookConfig> caffeineCache,
                                 RedisTemplate<String, Object> redisTemplate,
                                 WebhookConfigRepository repository) {
        this.caffeineCache = caffeineCache;
        this.redisTemplate = redisTemplate;
        this.repository = repository;
    }

    /**
     * Looks up the webhook com.rnvo.notifier.config for an (accountId, eventType) pair.
     * Checks Caffeine (L1), then Redis (L2), then Postgres (L3).
     * On miss, populates all upstream tiers.
     */
    public Optional<WebhookConfig> getConfig(String accountId, String eventType) {
        String key = accountId + ":" + eventType;

        // L1: Caffeine (in-process)
        WebhookConfig cached = caffeineCache.getIfPresent(key);
        if (cached != null) {
            log.debug("L1 cache hit for key={}", key);
            return Optional.of(cached);
        }

        // L2: Redis
        Object redisCached = redisTemplate.opsForValue().get(REDIS_PREFIX + key);
        if (redisCached instanceof WebhookConfig config) {
            log.debug("L2 cache hit for key={}", key);
            caffeineCache.put(key, config);
            return Optional.of(config);
        }

        // L3: PostgreSQL
        Optional<WebhookConfig> dbResult = repository.findByAccountIdAndEventType(accountId, eventType);
        dbResult.ifPresent(config -> {
            log.debug("L3 DB hit for key={}, populating caches", key);
            redisTemplate.opsForValue().set(REDIS_PREFIX + key, config, REDIS_TTL);
            caffeineCache.put(key, config);
        });

        return dbResult;
    }
}
