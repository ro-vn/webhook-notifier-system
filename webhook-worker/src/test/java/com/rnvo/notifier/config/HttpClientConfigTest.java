package com.rnvo.notifier.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {HttpClientConfig.class, VirtualThreadConfig.class})
@ActiveProfiles("test")
class HttpClientConfigTest {

    @Autowired
    HttpClient httpClient;

    @Test
    void httpClientBeanExistsWithConnectTimeout() {
        assertThat(httpClient).isNotNull();
        assertThat(httpClient.connectTimeout()).isPresent();
    }
}
