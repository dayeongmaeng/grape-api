package com.grape.api.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Binds the {@code app.*} tree from application.yml. See AGENTS.md "환경변수 / 설정". */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Oauth oauth) {

    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {
    }

    public record Oauth(Google google, Kakao kakao) {

        public record Google(String clientId) {
        }

        /** {@code restApiKey} is reserved for Kakao admin / token-info calls; /v2/user/me needs only the user token. */
        public record Kakao(String restApiKey) {
        }
    }
}
