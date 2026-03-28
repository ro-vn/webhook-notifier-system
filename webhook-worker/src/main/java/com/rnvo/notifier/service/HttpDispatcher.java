package com.rnvo.notifier.service;

import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Dispatches webhook payloads via HTTP POST.
 * Wrapped with Resilience4j @Retry for exponential backoff on 5xx errors.
 */
@Service
public class HttpDispatcher {

    private static final Logger log = LoggerFactory.getLogger(HttpDispatcher.class);

    private final HttpClient httpClient;

    public HttpDispatcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Sends a POST request with the given JSON payload to the target URL.
     * Returns true on 2xx success. Throws on 5xx (triggering retries).
     * Falls back to returning false when all retries are exhausted.
     */
    @Retry(name = "webhookRetry", fallbackMethod = "onRetryExhausted")
    public boolean dispatch(String targetUrl, String jsonPayload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 500) {
                log.warn("Received {} from {}", response.statusCode(), targetUrl);
                throw new ServerErrorException(response.statusCode());
            }

            log.debug("Webhook delivered to {} — HTTP {}", targetUrl, response.statusCode());
            return true;

        } catch (IOException e) {
            log.error("IO error dispatching to {}: {}", targetUrl, e.getMessage());
            throw new RuntimeException("IO error during webhook dispatch", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during webhook dispatch", e);
        }
    }

    /**
     * Fallback method called when all retry attempts are exhausted.
     * Signals the caller to persist the event to the DLQ.
     */
    @SuppressWarnings("unused")
    private boolean onRetryExhausted(String targetUrl, String payload, Exception ex) {
        log.error("All retries exhausted for {}. Reason: {}", targetUrl, ex.getMessage());
        return false;
    }
}
