package com.rnvo.notifier.service;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces fairness by checking if an account has exceeded its event processing quota.
 * Uses Redisson's distributed rate limiter for cross-worker coordination.
 */
@Service
public class FairnessEnforcer {

    private static final Logger log = LoggerFactory.getLogger(FairnessEnforcer.class);

    private final RedissonClient redissonClient;

    @Value("${app.fairness.limit:100}")
    private long rateLimit;

    @Value("${app.fairness.interval-seconds:1}")
    private long intervalSeconds;

    private final Map<String, RRateLimiter> limiters = new ConcurrentHashMap<>();

    public FairnessEnforcer(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * Checks if the given account is allowed to process another event in the "VIP" lane.
     *
     * @param accountId the account ID to check
     * @return true if allowed (under limit), false if rate limited (whale)
     */
    public boolean isAllowed(String accountId) {
        RRateLimiter rateLimiter = limiters.computeIfAbsent(accountId, id -> {
            RRateLimiter limiter = redissonClient.getRateLimiter("fairness:limiter:" + id);
            // Initialize if not already set. 
            // Note: trySetRate is atomic and only sets if not already initialized.
            limiter.trySetRate(RateType.OVERALL, rateLimit, Duration.ofSeconds(intervalSeconds));
            return limiter;
        });

        boolean allowed = rateLimiter.tryAcquire(1);
        if (!allowed) {
            log.warn("Account {} exceeded fairness limit of {}/{}s. Diverting to low lane.", accountId, rateLimit, intervalSeconds);
        }
        return allowed;
    }
}
