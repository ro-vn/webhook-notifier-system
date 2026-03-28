package com.rnvo.notifier.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FairnessEnforcerTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RRateLimiter rateLimiter;

    private FairnessEnforcer enforcer;

    @BeforeEach
    void setUp() {
        enforcer = new FairnessEnforcer(redissonClient);
        when(redissonClient.getRateLimiter("fairness:test_account")).thenReturn(rateLimiter);
        when(rateLimiter.trySetRate(any(), anyLong(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("Allows when under rate limit")
    void shouldAllowWhenUnderLimit() {
        when(rateLimiter.tryAcquire()).thenReturn(true);

        assertThat(enforcer.isAllowed("test_account")).isTrue();
    }

    @Test
    @DisplayName("Blocks when rate limit exceeded")
    void shouldBlockWhenLimitExceeded() {
        when(rateLimiter.tryAcquire()).thenReturn(false);

        assertThat(enforcer.isAllowed("test_account")).isFalse();
    }
}
