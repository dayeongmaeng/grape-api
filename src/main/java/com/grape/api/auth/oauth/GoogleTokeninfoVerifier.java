package com.grape.api.auth.oauth;

import com.grape.api.common.ApiException;
import com.grape.api.common.ErrorCode;
import com.grape.api.common.config.AppProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Set;

/**
 * Verifies the ID token via Google's {@code tokeninfo} endpoint (Google checks the signature and
 * expiry server-side); we then assert {@code aud} and {@code iss}. A local JWKS verification could
 * replace this later, but the client OAuth flow is not wired yet (see server-design-draft.md §4).
 */
@Component
class GoogleTokeninfoVerifier implements GoogleTokenVerifier {

    private static final String BASE_URL = "https://oauth2.googleapis.com";
    private static final Set<String> VALID_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final RestClient restClient;
    private final String clientId;

    GoogleTokeninfoVerifier(AppProperties props, RestClient.Builder builder) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.clientId = props.oauth().google().clientId();
    }

    @Override
    public OAuthUserInfo verify(String idToken) {
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
        if (clientId == null || clientId.isBlank() || !clientId.equals(body.get("aud"))) {
            throw new ApiException(ErrorCode.INVALID_GOOGLE_TOKEN, "Google token audience mismatch");
        }
        if (!VALID_ISSUERS.contains(String.valueOf(body.get("iss")))) {
            throw new ApiException(ErrorCode.INVALID_GOOGLE_TOKEN, "Google token issuer invalid");
        }
        String sub = (String) body.get("sub");
        if (sub == null || sub.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_GOOGLE_TOKEN, "Google token has no subject");
        }
        return new OAuthUserInfo(sub, (String) body.get("email"), (String) body.get("name"));
    }
}
