package com.rnvo.notifier.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * Configures the Java HttpClient used for webhook HTTP dispatch.
 * Injects the virtual-thread executor so HTTP/2 internals and
 * blocking send() calls park virtual threads instead of consuming
 * a platform carrier thread during network wait.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient httpClient(@Qualifier("virtualThreadExecutor") Executor executor) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(executor)
                .build();
    }
}
