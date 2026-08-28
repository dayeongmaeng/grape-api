package com.grape.api.auth.oauth;

import com.grape.api.common.ApiException;
import com.grape.api.common.ErrorCode;
import com.grape.api.common.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The {@code aud} claim is matched against every configured client ID (web + iOS + Android), the way
 * kkori-api's {@code GoogleOAuthVerifier} does — the native auth-code + PKCE flow mints tokens whose
 * {@code aud} is the native client, not the web one.
 */
class GoogleTokeninfoVerifierTest {

    private MockRestServiceServer server;

    @Test
    void acceptsTokenWhoseAudienceIsTheWebClientId() {
        GoogleTokeninfoVerifier verifier = verifierFor("web-id", "ios-id", null);
        expectTokeninfo(tokeninfo("web-id", "true"));

        OAuthUserInfo info = verifier.verify("tok");

        assertThat(info.providerUserId()).isEqualTo("google-sub");
        assertThat(info.email()).isEqualTo("u@example.com");
        assertThat(info.nickname()).isEqualTo("Grape");
        server.verify();
    }

    @Test
    void acceptsTokenWhoseAudienceIsANativeClientId() {
        GoogleTokeninfoVerifier verifier = verifierFor("web-id", "ios-id", "android-id");
        expectTokeninfo(tokeninfo("android-id", "true"));

        assertThat(verifier.verify("tok").providerUserId()).isEqualTo("google-sub");
        server.verify();
    }

    @Test
    void rejectsTokenWhoseAudienceMatchesNoConfiguredClientId() {
        GoogleTokeninfoVerifier verifier = verifierFor("web-id", "ios-id", null);
        expectTokeninfo(tokeninfo("someone-elses-id", "true"));

        assertThatThrownBy(() -> verifier.verify("tok"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_GOOGLE_TOKEN));
    }

    @Test
    void rejectsTokenWithUnverifiedEmail() {
        GoogleTokeninfoVerifier verifier = verifierFor("web-id", null, null);
        expectTokeninfo(tokeninfo("web-id", "false"));

        assertThatThrownBy(() -> verifier.verify("tok"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_GOOGLE_TOKEN));
    }

    private GoogleTokeninfoVerifier verifierFor(String web, String ios, String android) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AppProperties props = new AppProperties(
                null,
                new AppProperties.Oauth(
                        new AppProperties.Oauth.Google(web, ios, android),
                        new AppProperties.Oauth.Kakao(null, null)),
                null);
        return new GoogleTokeninfoVerifier(props, builder);
    }

    private void expectTokeninfo(String responseBody) {
        server.expect(requestTo("https://oauth2.googleapis.com/tokeninfo?id_token=tok"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }

    private String tokeninfo(String aud, String emailVerified) {
        return """
                {
                  "sub": "google-sub",
                  "aud": "%s",
                  "iss": "https://accounts.google.com",
                  "email": "u@example.com",
                  "email_verified": "%s",
                  "name": "Grape"
                }
                """.formatted(aud, emailVerified);
    }
}
