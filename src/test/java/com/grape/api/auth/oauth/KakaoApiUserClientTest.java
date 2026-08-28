package com.grape.api.auth.oauth;

import com.grape.api.common.ApiException;
import com.grape.api.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@code GET /v2/user/me} resolves the Kakao identity; a non-2xx (typically {@code code=-401} for an
 * expired / invalid access token) now surfaces the status in the {@link ApiException} message and is
 * logged, instead of failing silently.
 */
class KakaoApiUserClientTest {

    private static final String USER_ME_URL = "https://kapi.kakao.com/v2/user/me";

    private MockRestServiceServer server;

    @Test
    void mapsIdEmailAndNicknameFromKakaoResponse() {
        KakaoApiUserClient client = client();
        server.expect(requestTo(USER_ME_URL))
                .andExpect(header("Authorization", "Bearer kakao-access-xyz"))
                .andRespond(withSuccess("""
                        {
                          "id": 4200000001,
                          "kakao_account": {
                            "email": "k@example.com",
                            "profile": { "nickname": "케이" }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        OAuthUserInfo info = client.fetchUser("kakao-access-xyz");

        assertThat(info.providerUserId()).isEqualTo("4200000001");
        assertThat(info.email()).isEqualTo("k@example.com");
        assertThat(info.nickname()).isEqualTo("케이");
        server.verify();
    }

    @Test
    void surfacesStatusWhenKakaoRejectsTheAccessToken() {
        KakaoApiUserClient client = client();
        server.expect(requestTo(USER_ME_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"msg\":\"this access token does not exist\",\"code\":-401}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchUser("expired-token"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_KAKAO_TOKEN);
                    assertThat(e.getMessage()).contains("401");
                });
    }

    @Test
    void rejectsResponseWithoutId() {
        KakaoApiUserClient client = client();
        server.expect(requestTo(USER_ME_URL))
                .andRespond(withSuccess("{\"kakao_account\":{}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchUser("kakao-access-xyz"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_KAKAO_TOKEN));
    }

    private KakaoApiUserClient client() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new KakaoApiUserClient(builder);
    }
}
