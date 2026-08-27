package com.grape.api.bunch.dto;

import com.grape.api.harvest.dto.HarvestResponse;

/** {@code POST /api/bunches/{id}/replant} — the harvest snapshot plus the reset (still-alive) bunch. */
public record ReplantResponse(HarvestResponse harvest, BunchResponse bunch) {
}
