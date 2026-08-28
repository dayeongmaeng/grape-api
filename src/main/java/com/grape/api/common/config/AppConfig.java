package com.grape.api.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class AppConfig {

    /** Injected wherever "now" is needed, so tests can pin the clock. Instant is zone-independent. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Boot's {@code spring-boot-starter-webmvc} does not contribute the HTTP-client auto-config, so
     * we expose a builder for the OAuth verifier clients. 5s connect / 5s read timeouts keep a hung
     * Google / Kakao endpoint from pinning a request thread (same limits as kkori-api's RestClientConfig).
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory);
    }
}
