package com.rnvo.notifier.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
 */
@Service
public class HttpDispatcher {

    private static final Logger log = LoggerFactory.getLogger(HttpDispatcher.class);

    private final HttpClient httpClient;
    private final MeterRegistry meterRegistry;

    public HttpDispatcher(HttpClient httpClient, MeterRegistry meterRegistry) {
        this.httpClient = httpClient;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Sends a POST request with the given JSON payload to the target URL.
     * Returns true on 2xx success. Throws on 5xx (triggering retries).
     */
    public void dispatch(String targetUrl, String jsonPayload) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 500) {
                log.debug("Received {} from {}", response.statusCode(), targetUrl);
                sample.stop(meterRegistry.timer("webhook.delivery.latency", "outcome", "server_error"));
                throw new ServerErrorException(response.statusCode());
            }

            log.debug("Webhook delivered to {} — HTTP {}", targetUrl, response.statusCode());
            sample.stop(meterRegistry.timer("webhook.delivery.latency", "outcome", "success"));

        } catch (IOException e) {
            log.error("IO error dispatching to {}: {}", targetUrl, e.getMessage());
            sample.stop(meterRegistry.timer("webhook.delivery.latency", "outcome", "io_error"));
            throw new RuntimeException("IO error during webhook dispatch", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sample.stop(meterRegistry.timer("webhook.delivery.latency", "outcome", "interrupted"));

            throw new RuntimeException("Interrupted during webhook dispatch", e);
        }
    }
}
