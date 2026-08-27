package com.grape.api.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
public class AppConfig {

    /** Injected wherever "now" is needed, so tests can pin the clock. Instant is zone-independent. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Boot's {@code spring-boot-starter-webmvc} does not contribute the HTTP-client auto-config, so
     * we expose a plain builder for the OAuth verifier clients.
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
