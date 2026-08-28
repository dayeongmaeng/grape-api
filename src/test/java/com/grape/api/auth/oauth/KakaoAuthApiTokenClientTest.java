package com.grape.api.auth.oauth;

import com.grape.api.common.ApiException;
import com.grape.api.common.ErrorCode;
import com.grape.api.common.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The 4xx body from Kakao's {@code /oauth/token} is surfaced in the {@link ApiException} message
 * (dev-facing only) so the usual setup failures — KOE320 (code reused / redirect_uri mismatch),
 * KOE101, {@code invalid_client} (client secret enabled but not sent) — are diagnosable from the
 * response during integration.
 */
class KakaoAuthApiTokenClientTest {

    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";

    private MockRestServiceServer server;

    @Test
    void returnsAccessTokenOnSuccess() {
        KakaoAuthApiTokenClient client = clientFor("rest-key", null);
        server.expect(requestTo(TOKEN_URL)).andExpect(method(POST))
                .andRespond(withSuccess("{\"access_token\":\"kakao-access-xyz\"}", MediaType.APPLICATION_JSON));

        assertThat(client.exchangeCode("auth-code", "http://localhost:8081/callback")).isEqualTo("kakao-access-xyz");
        server.verify();
    }

    @Test
    void surfacesKakaoErrorCodeWhenExchangeIsRejected() {
        KakaoAuthApiTokenClient client = clientFor("rest-key", null);
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"invalid_grant\",\"error_code\":\"KOE320\",\"error_description\":\"authorization code not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchangeCode("used-code", "http://localhost:8081/callback"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_KAKAO_TOKEN);
                    assertThat(e.getMessage()).contains("KOE320").contains("invalid_grant");
                    // error_description can echo the auth code — it must not leak into the message.
                    assertThat(e.getMessage()).doesNotContain("authorization code not found");
                });
    }

    @Test
    void rejectsResponseWithoutAccessToken() {
        KakaoAuthApiTokenClient client = clientFor("rest-key", null);
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"token_type\":\"bearer\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchangeCode("auth-code", "http://localhost:8081/callback"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_KAKAO_TOKEN));
    }

    @Test
    void failsWhenRestApiKeyIsNotConfigured() {
        KakaoAuthApiTokenClient client = clientFor("  ", null);

        assertThatThrownBy(() -> client.exchangeCode("auth-code", "http://localhost:8081/callback"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_KAKAO_TOKEN);
                    assertThat(e.getMessage()).contains("not configured");
                });
    }

    private KakaoAuthApiTokenClient clientFor(String restApiKey, String clientSecret) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AppProperties props = new AppProperties(
                null,
                new AppProperties.Oauth(
                        new AppProperties.Oauth.Google(null, null, null),
                        new AppProperties.Oauth.Kakao(restApiKey, clientSecret)),
                null);
        return new KakaoAuthApiTokenClient(props, builder);
    }
}
