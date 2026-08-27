package com.grape.api.account;

import com.grape.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountIntegrationTest extends IntegrationTest {

    @Test
    void me_returnsIdentity() throws Exception {
        String token = guestToken();
        mockMvc.perform(get("/api/users/me").header("Authorization", authHeader(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("GUEST"))
                .andExpect(jsonPath("$.email").isEmpty())
                .andExpect(jsonPath("$.nickname").isEmpty());
    }

    @Test
    void settings_returnsDefaults_thenAppliesPartialUpdate() throws Exception {
        String token = guestToken();

        String defaults = bodyOf(mockMvc.perform(get("/api/settings").header("Authorization", authHeader(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyReminder").value(true))
                .andExpect(jsonPath("$.fillSound").value(true)));
        assertThat(this.<String>read(defaults, "$.reminderTime")).isEqualTo("저녁 9:00");

        mockMvc.perform(patch("/api/settings").header("Authorization", authHeader(token))
                        .contentType(APPLICATION_JSON).content("{\"fillSound\":false,\"reminderTime\":\"morning 7\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyReminder").value(true))   // untouched
                .andExpect(jsonPath("$.fillSound").value(false))
                .andExpect(jsonPath("$.reminderTime").value("morning 7"));
    }

    @Test
    void deleteMe_hardDeletesAndCascades() throws Exception {
        String token = guestToken();
        String id = userId(token);

        String bunchId = createBunch(token, "x", "", 3, 0);
        fill(token, bunchId, 2);
        String other = createBunch(token, "y", "", 2, 0);
        fill(token, other, 2);
        mockMvc.perform(post("/api/bunches/{id}/archive", other).header("Authorization", authHeader(token)))
                .andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM bunches WHERE user_id = ?::uuid", id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM harvests WHERE user_id = ?::uuid", id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM bunch_fill_events")).isPositive();

        mockMvc.perform(delete("/api/users/me").header("Authorization", authHeader(token)))
                .andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM users WHERE id = ?::uuid", id)).isZero();
        assertThat(count("SELECT count(*) FROM bunches WHERE user_id = ?::uuid", id)).isZero();
        assertThat(count("SELECT count(*) FROM harvests WHERE user_id = ?::uuid", id)).isZero();
        assertThat(count("SELECT count(*) FROM user_settings WHERE user_id = ?::uuid", id)).isZero();
        assertThat(count("SELECT count(*) FROM refresh_tokens WHERE user_id = ?::uuid", id)).isZero();
        assertThat(count("SELECT count(*) FROM bunch_fill_events")).isZero();
    }
}
