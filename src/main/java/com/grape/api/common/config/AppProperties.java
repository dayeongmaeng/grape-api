package com.grape.api.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/** Binds the {@code app.*} tree from application.yml. See AGENTS.md "환경변수 / 설정". */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Oauth oauth, Cors cors) {

    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {
    }

    public record Oauth(Google google, Kakao kakao) {

        public record Google(String clientId) {
        }

        /** {@code restApiKey} is reserved for Kakao admin / token-info calls; /v2/user/me needs only the user token. */
        public record Kakao(String restApiKey) {
        }
    }

    /** Browser origins allowed to call {@code /api/**}. Bound from a comma-separated string. */
    public record Cors(List<String> allowedOrigins) {
    }
}
