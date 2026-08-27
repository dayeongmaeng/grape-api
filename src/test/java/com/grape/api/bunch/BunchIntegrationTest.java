package com.grape.api.bunch;

import com.grape.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BunchIntegrationTest extends IntegrationTest {

    @Test
    void create_derivesDetailFromUnitLabel() throws Exception {
        String token = guestToken();

        String withLabel = createBunch(token, "math", "problem", 5, 7);
        assertThat(this.<String>json(
                mockMvc.perform(get("/api/bunches/{id}", withLabel).header("Authorization", authHeader(token))),
                "$.detail")).isEqualTo("한 알 = problem");

        String noLabel = createBunch(token, "plain", "", 3, 0);
        assertThat(this.<String>json(
                mockMvc.perform(get("/api/bunches/{id}", noLabel).header("Authorization", authHeader(token))),
                "$.detail")).isEmpty();
    }

    @Test
    void fill_clampsAndFollowsCompletedAtBranches() throws Exception {
        String token = guestToken();
        String id = createBunch(token, "c", "", 3, 0);

        // increase below total -> one fill date, not completed
        fill(token, id, 2)
                .andExpect(jsonPath("$.filled").value(2))
                .andExpect(jsonPath("$.fillDates.length()").value(1))
                .andExpect(jsonPath("$.completedAt").isEmpty());

        // clamp above total -> filled == total, completedAt set, another fill date
        String completed = bodyOf(fill(token, id, 99)
                .andExpect(jsonPath("$.filled").value(3))
                .andExpect(jsonPath("$.fillDates.length()").value(2))
                .andExpect(jsonPath("$.completedAt").isNotEmpty()));
        String completedAt = read(completed, "$.completedAt");

        // re-fill at total (no increase) -> no new fill date, completedAt unchanged
        clock.advance(Duration.ofMinutes(5));
        fill(token, id, 3)
                .andExpect(jsonPath("$.fillDates.length()").value(2))
                .andExpect(jsonPath("$.completedAt").value(completedAt));

        // drop below total -> completedAt cleared, still no new fill date
        fill(token, id, 1)
                .andExpect(jsonPath("$.filled").value(1))
                .andExpect(jsonPath("$.fillDates.length()").value(2))
                .andExpect(jsonPath("$.completedAt").isEmpty());

        // negative -> clamp to 0
        fill(token, id, -10).andExpect(jsonPath("$.filled").value(0));
    }

    @Test
    void fill_keepsDuplicateSameDayDates() throws Exception {
        String token = guestToken();
        String id = createBunch(token, "d", "", 10, 0);

        fill(token, id, 1);
        clock.advance(Duration.ofMinutes(20)); // later the same KST day, access token still valid
        fill(token, id, 2);
        clock.advance(Duration.ofMinutes(20));
        fill(token, id, 3)
                .andExpect(jsonPath("$.fillDates.length()").value(3))
                .andExpect(jsonPath("$.fillDates[0]").value("2026-08-27"))
                .andExpect(jsonPath("$.fillDates[1]").value("2026-08-27"))
                .andExpect(jsonPath("$.fillDates[2]").value("2026-08-27"));
    }

    @Test
    void fillDate_isComputedInAsiaSeoulNotUtc() throws Exception {
        // 2026-03-10 15:30Z  ==  2026-03-11 00:30 KST  -> the calendar date differs
        clock.setInstant(Instant.parse("2026-03-10T15:30:00Z"));

        String token = guestToken();
        String id = createBunch(token, "tz", "", 5, 0);

        fill(token, id, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fillDates[0]").value("2026-03-11")); // KST, not "2026-03-10"
    }

    @Test
    void replant_keepsBunchRowAndFillHistory_startsNewCycle() throws Exception {
        String token = guestToken();
        String id = createBunch(token, "replant", "p", 2, 0);
        fill(token, id, 2); // completed, one fill date

        mockMvc.perform(post("/api/bunches/{id}/replant", id).header("Authorization", authHeader(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.harvest.count").value(2))        // == total
                .andExpect(jsonPath("$.harvest.sourceBunchId").value(id))
                .andExpect(jsonPath("$.bunch.id").value(id))            // same row survives
                .andExpect(jsonPath("$.bunch.filled").value(0))
                .andExpect(jsonPath("$.bunch.completions").value(1))
                .andExpect(jsonPath("$.bunch.completedAt").isEmpty())
                .andExpect(jsonPath("$.bunch.fillDates.length()").value(1)); // history carried across cycle

        mockMvc.perform(get("/api/bunches/{id}", id).header("Authorization", authHeader(token)))
                .andExpect(status().isOk());
    }

    @Test
    void archive_deletesBunchRowAndFillEvents_countIsTotal() throws Exception {
        String token = guestToken();
        String id = createBunch(token, "archive", "", 5, 0);
        fill(token, id, 3); // NOT complete

        mockMvc.perform(post("/api/bunches/{id}/archive", id).header("Authorization", authHeader(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.harvest.count").value(5))  // == total, though filled was 3
                .andExpect(jsonPath("$.bunch").doesNotExist());

        mockMvc.perform(get("/api/bunches/{id}", id).header("Authorization", authHeader(token)))
                .andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM bunch_fill_events WHERE bunch_id = ?::uuid", id)).isZero();
    }

    @Test
    void bunchesOfOtherUsers_areNotVisible() throws Exception {
        String owner = guestToken();
        String id = createBunch(owner, "mine", "", 3, 0);

        String stranger = guestToken();
        mockMvc.perform(get("/api/bunches/{id}", id).header("Authorization", authHeader(stranger)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/bunches/{id}/archive", id).header("Authorization", authHeader(stranger)))
                .andExpect(status().isNotFound());
        fill(stranger, id, 1).andExpect(status().isNotFound());
    }

    @Test
    void listReturnsNewestFirst_andEmptyArrayWhenNone() throws Exception {
        String token = guestToken();
        mockMvc.perform(get("/api/bunches").header("Authorization", authHeader(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        createBunch(token, "first", "", 1, 0);
        clock.advance(Duration.ofSeconds(1));
        createBunch(token, "second", "", 1, 0);

        mockMvc.perform(get("/api/bunches").header("Authorization", authHeader(token)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("second"))
                .andExpect(jsonPath("$[1].name").value("first"));
    }

    @Test
    void unauthenticatedRequestsAreRejectedWithJsonBody() throws Exception {
        mockMvc.perform(get("/api/bunches"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void invalidCreateBody_is400WithValidationCode() throws Exception {
        String token = guestToken();
        mockMvc.perform(post("/api/bunches").header("Authorization", authHeader(token))
                        .contentType(APPLICATION_JSON).content("{\"name\":\"\",\"total\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
