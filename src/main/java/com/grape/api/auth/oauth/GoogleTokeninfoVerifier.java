package com.grape.api.auth.oauth;

import com.grape.api.common.ApiException;
import com.grape.api.common.ErrorCode;
import com.grape.api.common.config.AppProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verifies the ID token via Google's {@code tokeninfo} endpoint (Google checks the signature and
 * expiry server-side); we then assert {@code aud}, {@code iss}, and {@code email_verified}.
 *
 * <p>{@code aud} is matched against <em>every</em> configured client ID — the Web client (web login)
 * and the iOS / Android native clients — because the client's native auth-code + PKCE flow mints a
 * token whose {@code aud} is the native client, not the Web one. This mirrors kkori-api's
 * {@code GoogleOAuthVerifier}. A local JWKS verification could replace the network call later.
 */
@Component
class GoogleTokeninfoVerifier implements GoogleTokenVerifier {

    private static final String BASE_URL = "https://oauth2.googleapis.com";
    private static final Set<String> VALID_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final RestClient restClient;
    private final List<String> allowedAudiences;

    GoogleTokeninfoVerifier(AppProperties props, RestClient.Builder builder) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.allowedAudiences = props.oauth().google().allowedAudiences();
    }

    @Override
    public OAuthUserInfo verify(String idToken) {
        if (allowedAudiences.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_GOOGLE_TOKEN, "No Google client ID configured");
        }

        Map<String, Object> body;
        try {
            body = restClient.get()
                    .uri(uri -> uri.path("/tokeninfo").queryParam("id_token", idToken).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientException ex) {
            throw new ApiException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }
        if (body == null) {
            throw new ApiException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }
        if (!allowedAudiences.contains(String.valueOf(body.get("aud")))) {
            throw new ApiException(ErrorCode.INVALID_GOOGLE_TOKEN, "Google token audience mismatch");
        }
        if (!VALID_ISSUERS.contains(String.valueOf(body.get("iss")))) {
            throw new ApiException(ErrorCode.INVALID_GOOGLE_TOKEN, "Google token issuer invalid");
        }
        // tokeninfo returns this as the string "true"/"false"; a boolean would stringify the same way.
        if (!"true".equals(String.valueOf(body.get("email_verified")))) {
            throw new ApiException(ErrorCode.INVALID_GOOGLE_TOKEN, "Google token email not verified");
        }
        String sub = (String) body.get("sub");
        if (sub == null || sub.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_GOOGLE_TOKEN, "Google token has no subject");
        }
        return new OAuthUserInfo(sub, (String) body.get("email"), (String) body.get("name"));
    }
}
