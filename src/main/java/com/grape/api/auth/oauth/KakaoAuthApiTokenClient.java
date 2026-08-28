package com.grape.api.auth.oauth;

import com.grape.api.common.ApiException;
import com.grape.api.common.ErrorCode;
import com.grape.api.common.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calls Kakao's {@code POST /oauth/token} (Kauth) with {@code grant_type=authorization_code} to turn
 * the redirect code into a user access token. Uses {@code app.oauth.kakao.rest-api-key} as the
 * {@code client_id}; {@code client-secret} is added only when configured.
 *
 * <p>Kakao's 4xx body ({@code error} / {@code error_code}) is logged at WARN so the usual setup
 * failures are diagnosable: <b>KOE101</b> (client_id / redirect_uri not registered on the app),
 * <b>KOE320</b> (code already used, expired, or issued for a different redirect_uri),
 * {@code invalid_client} (client secret enabled on the app but not sent).
 */
@Slf4j
@Component
class KakaoAuthApiTokenClient implements KakaoTokenClient {

    private static final String BASE_URL = "https://kauth.kakao.com";

    private final RestClient restClient;
    private final String restApiKey;
    private final String clientSecret;

    KakaoAuthApiTokenClient(AppProperties props, RestClient.Builder builder) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.restApiKey = props.oauth().kakao().restApiKey();
        this.clientSecret = props.oauth().kakao().clientSecret();
    }

    @Override
    public String exchangeCode(String code, String redirectUri) {
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_KAKAO_TOKEN, "Kakao REST API key is not configured");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", restApiKey);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        log.info("Kakao token exchange: redirectUri={}, clientId={}, clientSecretSent={}",
                redirectUri, mask(restApiKey), clientSecret != null && !clientSecret.isBlank());

        Map<String, Object> body;
        try {
            body = restClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            log.warn("Kakao token exchange rejected: status={}, {}, redirectUri={}",
                    ex.getStatusCode().value(), summarizeKakaoError(responseBody), redirectUri);
            // Kakao's error / error_code is dev-facing only (not part of the API contract, see ErrorCode)
            // — surfacing it makes the usual setup failures (KOE101 / KOE320 / invalid_client) diagnosable.
            throw new ApiException(ErrorCode.INVALID_KAKAO_TOKEN,
                    "Kakao token exchange failed (" + summarizeKakaoError(responseBody) + ")");
        } catch (RestClientException ex) {
            log.warn("Kakao token exchange request error: {}", ex.getMessage());
            throw new ApiException(ErrorCode.INVALID_KAKAO_TOKEN, "Kakao token exchange request failed");
        }
        if (body == null || !(body.get("access_token") instanceof String accessToken) || accessToken.isBlank()) {
            log.warn("Kakao token exchange returned no access_token: body={}", body);
            throw new ApiException(ErrorCode.INVALID_KAKAO_TOKEN, "Kakao token exchange returned no access token");
        }
        return accessToken;
    }

    /** First 6 chars only — enough to tell two keys apart in a log without exposing the key. */
    private static String mask(String key) {
        return key.substring(0, Math.min(6, key.length())) + "***";
    }

    /** {@code error} + {@code error_code} only — {@code error_description} can echo the auth code. */
    private static String summarizeKakaoError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "error=(empty body)";
        }
        return "error=" + jsonString(responseBody, "error") + " error_code=" + jsonString(responseBody, "error_code");
    }

    private static String jsonString(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "?";
    }
}
