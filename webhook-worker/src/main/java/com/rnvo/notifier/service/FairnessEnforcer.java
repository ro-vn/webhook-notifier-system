package com.rnvo.notifier.service;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Per-account fairness enforcer using Redisson's distributed rate limiter.
 * Prevents whale accounts from starving smaller accounts.
 */
@Service
public class FairnessEnforcer {

    private static final Logger log = LoggerFactory.getLogger(FairnessEnforcer.class);

    private final RedissonClient redissonClient;

    @Value("${fairness.rate-limit:1000}")
    private int rateLimit;

    public FairnessEnforcer(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * Checks whether the given account is allowed to proceed.
     * Returns false if the account has exceeded its per-second concurrency limit.
     */
    public boolean isAllowed(String accountId) {
        RRateLimiter limiter = redissonClient.getRateLimiter("fairness:" + accountId);
        limiter.trySetRate(RateType.PER_CLIENT, rateLimit, Duration.ofSeconds(1));
        boolean allowed = limiter.tryAcquire();
        if (!allowed) {
            log.warn("Rate limit exceeded for account={}", accountId);
        }
        return allowed;
    }
}
