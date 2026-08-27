package com.grape.api.harvest;

import com.grape.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HarvestIntegrationTest extends IntegrationTest {

    /** archive a completed bunch and return the harvest id. */
    private String archive(String token, String name, String unitLabel, int total, int fillTo) throws Exception {
        String bunchId = createBunch(token, name, unitLabel, total, 0);
        fill(token, bunchId, fillTo);
        String body = bodyOf(mockMvc.perform(post("/api/bunches/{id}/archive", bunchId)
                .header("Authorization", authHeader(token))).andExpect(status().isOk()));
        return read(body, "$.harvest.id");
    }

    @Test
    void recall_createsFreshBunchWithNewId_andDeletesHarvest() throws Exception {
        String token = guestToken();
        String bunchId = createBunch(token, "src", "unit", 4, 7);
        fill(token, bunchId, 4);
        String archived = bodyOf(mockMvc.perform(post("/api/bunches/{id}/archive", bunchId)
                .header("Authorization", authHeader(token))).andExpect(status().isOk()));
        String harvestId = read(archived, "$.harvest.id");

        String recalled = bodyOf(mockMvc.perform(post("/api/harvests/{id}/recall", harvestId)
                        .header("Authorization", authHeader(token))
                        .contentType(APPLICATION_JSON).content("{\"filled\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))       // == harvest.count
                .andExpect(jsonPath("$.filled").value(2))
                .andExpect(jsonPath("$.detail").value(""))
                .andExpect(jsonPath("$.unitLabel").value(""))
                .andExpect(jsonPath("$.periodDays").value(0))
                .andExpect(jsonPath("$.completions").value(0))
                .andExpect(jsonPath("$.completedAt").isEmpty())
                .andExpect(jsonPath("$.fillDates.length()").value(0)));

        assertThat(this.<String>read(recalled, "$.id")).isNotEqualTo(bunchId);

        mockMvc.perform(get("/api/harvests/{id}", harvestId).header("Authorization", authHeader(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void recall_clampsFilledToHarvestCount() throws Exception {
        String token = guestToken();
        String harvestId = archive(token, "h", "", 3, 3);

        mockMvc.perform(post("/api/harvests/{id}/recall", harvestId).header("Authorization", authHeader(token))
                        .contentType(APPLICATION_JSON).content("{\"filled\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filled").value(3))
                .andExpect(jsonPath("$.completedAt").isEmpty()); // clamp to total but not marked completed
    }

    @Test
    void harvestKeepsSourceBunchId_afterSourceBunchIsGone() throws Exception {
        String token = guestToken();
        String bunchId = createBunch(token, "orphan", "", 1, 0);
        fill(token, bunchId, 1);
        String archived = bodyOf(mockMvc.perform(post("/api/bunches/{id}/archive", bunchId)
                .header("Authorization", authHeader(token))).andExpect(status().isOk()));
        String harvestId = read(archived, "$.harvest.id");

        // source bunch row is gone...
        mockMvc.perform(get("/api/bunches/{id}", bunchId).header("Authorization", authHeader(token)))
                .andExpect(status().isNotFound());

        // ...but the harvest still resolves and still points at the now-missing bunch
        mockMvc.perform(get("/api/harvests/{id}", harvestId).header("Authorization", authHeader(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceBunchId").value(bunchId));

        assertThat(count("SELECT count(*) FROM harvests WHERE source_bunch_id = ?::uuid", bunchId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM bunches WHERE id = ?::uuid", bunchId)).isZero();
    }

    @Test
    void listNewestFirst_getAndDelete_ownershipEnforced() throws Exception {
        String owner = guestToken();
        archive(owner, "a", "", 2, 2);
        clock.advance(java.time.Duration.ofSeconds(1));
        String second = archive(owner, "b", "", 2, 2);

        mockMvc.perform(get("/api/harvests").header("Authorization", authHeader(owner)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("b"));

        String stranger = guestToken();
        mockMvc.perform(get("/api/harvests/{id}", second).header("Authorization", authHeader(stranger)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/harvests/{id}", second).header("Authorization", authHeader(stranger)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/harvests/{id}", second).header("Authorization", authHeader(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/harvests").header("Authorization", authHeader(owner)))
                .andExpect(jsonPath("$.length()").value(1));
    }
}
