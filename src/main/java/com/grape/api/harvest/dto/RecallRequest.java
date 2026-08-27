package com.grape.api.harvest.dto;

import jakarta.validation.constraints.NotNull;

/** {@code POST /api/harvests/{id}/recall}. {@code filled} is clamped to [0, harvest.count]. */
public record RecallRequest(@NotNull Integer filled) {
}
