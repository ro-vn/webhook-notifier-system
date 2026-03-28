package com.rnvo.notifier.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpDispatcherTest {

    @Mock private HttpClient httpClient;
    @Mock private HttpResponse<String> httpResponse;

    private HttpDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new HttpDispatcher(httpClient);
    }

    @Test
    @DisplayName("Returns true on 200 OK response")
    @SuppressWarnings("unchecked")
    void shouldReturnTrueOnSuccess() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);

        boolean result = dispatcher.dispatch("http://example.com/webhook", "{\"test\":true}");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Throws ServerErrorException on 503 response")
    @SuppressWarnings("unchecked")
    void shouldThrowOnServerError() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(503);

        assertThatThrownBy(() -> dispatcher.dispatch("http://example.com/webhook", "{\"test\":true}"))
                .isInstanceOf(ServerErrorException.class);
    }

    @Test
    @DisplayName("Throws RuntimeException on IO error")
    @SuppressWarnings("unchecked")
    void shouldThrowOnIOError() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        assertThatThrownBy(() -> dispatcher.dispatch("http://example.com/webhook", "{\"test\":true}"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("IO error");
    }
}
