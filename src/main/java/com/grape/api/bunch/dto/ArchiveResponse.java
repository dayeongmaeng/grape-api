package com.grape.api.bunch.dto;

import com.grape.api.harvest.dto.HarvestResponse;

/** {@code POST /api/bunches/{id}/archive} — the harvest snapshot; the bunch row is deleted. */
public record ArchiveResponse(HarvestResponse harvest) {
}
