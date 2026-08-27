package com.grape.api.support;

import com.grape.api.TestcontainersConfiguration;
import com.grape.api.auth.oauth.GoogleTokenVerifier;
import com.grape.api.auth.oauth.KakaoUserClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for API-level integration tests. Real Postgres (Testcontainers) + full MVC/Security stack via
 * {@link MockMvc}. OAuth verifier clients are Mockito beans; the clock is a {@link MutableClock} so
 * date/expiry boundaries are deterministic. Each test starts from an empty database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, IntegrationTest.Config.class})
public abstract class IntegrationTest {

    /** 2026-08-27 02:00Z == 11:00 KST. */
    protected static final Instant BASE_TIME = Instant.parse("2026-08-27T02:00:00Z");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @MockitoBean
    protected GoogleTokenVerifier googleTokenVerifier;

    @MockitoBean
    protected KakaoUserClient kakaoUserClient;

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(BASE_TIME);
        }
    }

    @BeforeEach
    void resetState() {
        clock.setInstant(BASE_TIME);
        jdbcTemplate.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    }

    // --- helpers ---------------------------------------------------------------------------------

    protected String bodyOf(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected <T> T json(ResultActions actions, String path) throws Exception {
        return JsonPath.read(bodyOf(actions), path);
    }

    protected <T> T read(String body, String path) {
        return JsonPath.read(body, path);
    }

    protected String authHeader(String token) {
        return "Bearer " + token;
    }

    protected String guestToken() throws Exception {
        return json(mockMvc.perform(post("/api/auth/guest")).andExpect(status().isOk()), "$.accessToken");
    }

    protected String userId(String accessToken) throws Exception {
        return json(mockMvc.perform(get("/api/users/me").header("Authorization", authHeader(accessToken))), "$.id");
    }

    protected String createBunch(String token, String name, String unitLabel, int total, int periodDays)
            throws Exception {
        String json = "{\"name\":\"%s\",\"unitLabel\":\"%s\",\"total\":%d,\"periodDays\":%d}"
                .formatted(name, unitLabel, total, periodDays);
        return json(mockMvc.perform(post("/api/bunches")
                        .header("Authorization", authHeader(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated()), "$.id");
    }

    protected ResultActions fill(String token, String bunchId, int filled) throws Exception {
        return mockMvc.perform(patch("/api/bunches/{id}/fill", bunchId)
                .header("Authorization", authHeader(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"filled\":" + filled + "}"));
    }

    protected int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }
}
