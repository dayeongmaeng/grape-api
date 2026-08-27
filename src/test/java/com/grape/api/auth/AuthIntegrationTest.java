package com.grape.api.auth;

import com.grape.api.auth.oauth.OAuthUserInfo;
import com.grape.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void guestLogin_issuesTokensAndCreatesUserAndSettings() throws Exception {
        String token = guestToken();
        String id = userId(token);

        assertThat(count("SELECT count(*) FROM users WHERE id = ?::uuid AND provider = 'GUEST'", id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM user_settings WHERE user_id = ?::uuid", id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM refresh_tokens WHERE user_id = ?::uuid", id)).isEqualTo(1);
    }

    @Test
    void refresh_rotatesAndInvalidatesTheOldToken() throws Exception {
        String login = bodyOf(mockMvc.perform(post("/api/auth/guest")).andExpect(status().isOk()));
        String rt1 = read(login, "$.refreshToken");

        String rotated = bodyOf(mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON).content("{\"refreshToken\":\"" + rt1 + "\"}"))
                .andExpect(status().isOk()));
        String rt2 = read(rotated, "$.refreshToken");
        assertThat(rt2).isNotEqualTo(rt1);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON).content("{\"refreshToken\":\"" + rt1 + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logout_revokesTheRefreshToken() throws Exception {
        String login = bodyOf(mockMvc.perform(post("/api/auth/guest")).andExpect(status().isOk()));
        String at = read(login, "$.accessToken");
        String rt = read(login, "$.refreshToken");

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", authHeader(at))
                        .contentType(APPLICATION_JSON).content("{\"refreshToken\":\"" + rt + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON).content("{\"refreshToken\":\"" + rt + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(APPLICATION_JSON).content("{\"refreshToken\":\"" + rt + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void expiredAccessToken_isRejected() throws Exception {
        String token = guestToken();
        mockMvc.perform(get("/api/users/me").header("Authorization", authHeader(token)))
                .andExpect(status().isOk());

        clock.advance(Duration.ofHours(2)); // access TTL is 1h

        mockMvc.perform(get("/api/users/me").header("Authorization", authHeader(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void guestMerge_caseA_noExistingAccount_convertsGuestRowInPlace() throws Exception {
        given(googleTokenVerifier.verify("google-token-a"))
                .willReturn(new OAuthUserInfo("google-sub-A", "a@example.com", "Ayla"));

        String guest = guestToken();
        String guestId = userId(guest);
        String bunchId = createBunch(guest, "keepme", "", 3, 0);

        String merged = bodyOf(mockMvc.perform(post("/api/auth/google")
                        .header("Authorization", authHeader(guest))
                        .contentType(APPLICATION_JSON).content("{\"idToken\":\"google-token-a\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(guestId))       // same row
                .andExpect(jsonPath("$.user.provider").value("GOOGLE")));

        String newToken = read(merged, "$.accessToken");
        mockMvc.perform(get("/api/bunches/{id}", bunchId).header("Authorization", authHeader(newToken)))
                .andExpect(status().isOk()); // bunch still owned (same user_id)

        assertThat(count("SELECT count(*) FROM users WHERE id = ?::uuid AND provider = 'GOOGLE' "
                + "AND provider_user_id = 'google-sub-A'", guestId)).isEqualTo(1);
    }

    @Test
    void guestMerge_caseB_existingAccount_movesDataThenDeletesGuest() throws Exception {
        given(googleTokenVerifier.verify("google-token-b"))
                .willReturn(new OAuthUserInfo("google-sub-B", "b@example.com", "Bo"));

        // account already created on "another device"
        String existing = bodyOf(mockMvc.perform(post("/api/auth/google")
                        .contentType(APPLICATION_JSON).content("{\"idToken\":\"google-token-b\"}"))
                .andExpect(status().isOk()));
        String existingId = read(existing, "$.user.id");

        // guest with local data
        String guest = guestToken();
        String guestId = userId(guest);
        String guestBunch = createBunch(guest, "moveme", "", 4, 0);
        fill(guest, guestBunch, 2);

        String merged = bodyOf(mockMvc.perform(post("/api/auth/google")
                        .header("Authorization", authHeader(guest))
                        .contentType(APPLICATION_JSON).content("{\"idToken\":\"google-token-b\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(existingId))); // target account, not the guest

        String targetToken = read(merged, "$.accessToken");
        mockMvc.perform(get("/api/bunches/{id}", guestBunch).header("Authorization", authHeader(targetToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fillDates.length()").value(1)); // fill history moved too

        assertThat(count("SELECT count(*) FROM users WHERE id = ?::uuid", guestId)).isZero();
        assertThat(count("SELECT count(*) FROM bunches WHERE id = ?::uuid AND user_id = ?::uuid",
                guestBunch, existingId)).isEqualTo(1);
    }
}
