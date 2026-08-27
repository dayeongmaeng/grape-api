package com.grape.api.auth.oauth;

import com.grape.api.common.ApiException;
import com.grape.api.common.ErrorCode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Resolves the Kakao identity by calling {@code GET /v2/user/me} with the user's access token.
 * A 2xx response means Kakao accepted the token. {@code app.oauth.kakao.rest-api-key} is not needed
 * for this call (reserved for admin / token-info endpoints).
 */
@Component
class KakaoApiUserClient implements KakaoUserClient {

    private static final String BASE_URL = "https://kapi.kakao.com";

    private final RestClient restClient;

    KakaoApiUserClient(RestClient.Builder builder) {
        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    @Override
    public OAuthUserInfo fetchUser(String kakaoAccessToken) {
        Map<String, Object> body;
        try {
            body = restClient.get()
                    .uri("/v2/user/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientException ex) {
            throw new ApiException(ErrorCode.INVALID_KAKAO_TOKEN);
        }
        if (body == null || body.get("id") == null) {
            throw new ApiException(ErrorCode.INVALID_KAKAO_TOKEN);
        }

        String providerUserId = body.get("id") instanceof Number number
                ? Long.toString(number.longValue())
                : String.valueOf(body.get("id"));

        String email = null;
        String nickname = null;
        if (body.get("kakao_account") instanceof Map<?, ?> account) {
            email = (String) account.get("email");
            if (account.get("profile") instanceof Map<?, ?> profile) {
                nickname = (String) profile.get("nickname");
            }
        }
        return new OAuthUserInfo(providerUserId, email, nickname);
    }
}
