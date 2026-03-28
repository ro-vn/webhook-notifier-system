package com.rnvo.notifier.service;

import com.rnvo.notifier.model.WebhookConfig;
import com.rnvo.notifier.repository.WebhookConfigRepository;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookConfigServiceTest {

    @Mock private Cache<String, WebhookConfig> caffeineCache;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private WebhookConfigRepository repository;

    private WebhookConfigService service;

    private static final String ACCOUNT_ID = "test_account";
    private static final String EVENT_TYPE = "subscriber.created";
    private static final String KEY = ACCOUNT_ID + ":" + EVENT_TYPE;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new WebhookConfigService(caffeineCache, redisTemplate, repository);
    }

    @Test
    @DisplayName("L1 hit: returns config from Caffeine, no Redis or DB call")
    void shouldReturnFromCaffeineOnL1Hit() {
        WebhookConfig config = new WebhookConfig(ACCOUNT_ID, EVENT_TYPE, "http://example.com");
        when(caffeineCache.getIfPresent(KEY)).thenReturn(config);

        Optional<WebhookConfig> result = service.getConfig(ACCOUNT_ID, EVENT_TYPE);

        assertThat(result).isPresent();
        assertThat(result.get().getTargetUrl()).isEqualTo("http://example.com");
        verify(valueOperations, never()).get(anyString());
        verify(repository, never()).findByAccountIdAndEventType(anyString(), anyString());
    }

    @Test
    @DisplayName("L1 miss, L2 hit: returns from Redis and populates Caffeine")
    void shouldReturnFromRedisOnL2Hit() {
        WebhookConfig config = new WebhookConfig(ACCOUNT_ID, EVENT_TYPE, "http://example.com");
        when(caffeineCache.getIfPresent(KEY)).thenReturn(null);
        when(valueOperations.get("webhook:" + KEY)).thenReturn(config);

        Optional<WebhookConfig> result = service.getConfig(ACCOUNT_ID, EVENT_TYPE);

        assertThat(result).isPresent();
        verify(caffeineCache).put(KEY, config);
        verify(repository, never()).findByAccountIdAndEventType(anyString(), anyString());
    }

    @Test
    @DisplayName("L1+L2 miss, L3 hit: returns from DB and populates both caches")
    void shouldReturnFromDbOnL3Hit() {
        WebhookConfig config = new WebhookConfig(ACCOUNT_ID, EVENT_TYPE, "http://example.com");
        when(caffeineCache.getIfPresent(KEY)).thenReturn(null);
        when(valueOperations.get("webhook:" + KEY)).thenReturn(null);
        when(repository.findByAccountIdAndEventType(ACCOUNT_ID, EVENT_TYPE))
                .thenReturn(Optional.of(config));

        Optional<WebhookConfig> result = service.getConfig(ACCOUNT_ID, EVENT_TYPE);

        assertThat(result).isPresent();
        verify(valueOperations).set(eq("webhook:" + KEY), eq(config), any(Duration.class));
        verify(caffeineCache).put(KEY, config);
    }

    @Test
    @DisplayName("All miss: returns empty when config not found anywhere")
    void shouldReturnEmptyWhenNotFound() {
        when(caffeineCache.getIfPresent(KEY)).thenReturn(null);
        when(valueOperations.get("webhook:" + KEY)).thenReturn(null);
        when(repository.findByAccountIdAndEventType(ACCOUNT_ID, EVENT_TYPE))
                .thenReturn(Optional.empty());

        Optional<WebhookConfig> result = service.getConfig(ACCOUNT_ID, EVENT_TYPE);

        assertThat(result).isEmpty();
    }
}
