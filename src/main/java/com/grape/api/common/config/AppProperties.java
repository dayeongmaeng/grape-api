package com.grape.api.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/** Binds the {@code app.*} tree from application.yml. See AGENTS.md "환경변수 / 설정". */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Oauth oauth, Cors cors) {

    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {
    }

    public record Oauth(Google google, Kakao kakao) {

        /**
         * Google's ID token {@code aud} is checked against every configured client ID: the "Web
         * application" client for web login, plus the iOS / Android native clients (the native
         * auth-code + PKCE flow mints a token whose {@code aud} is the native client, not the web
         * one). Same approach as kkori-api's {@code GoogleOAuthVerifier}. Native IDs are optional.
         */
        public record Google(String clientId, String iosClientId, String androidClientId) {

            public List<String> allowedAudiences() {
                return Stream.of(clientId, iosClientId, androidClientId)
                        .filter(id -> id != null && !id.isBlank())
                        .toList();
            }
        }

        /**
         * {@code restApiKey} exchanges the web login's authorization code for an access token
         * ({@code POST /api/auth/kakao/web}); native login sends the token directly and needs neither key.
         * {@code clientSecret} is sent on the token exchange only when the Kakao app has it enabled (nullable).
         */
        public record Kakao(String restApiKey, String clientSecret) {
        }
    }

    /** Browser origins allowed to call {@code /api/**}. Bound from a comma-separated string. */
    public record Cors(List<String> allowedOrigins) {
    }
}
