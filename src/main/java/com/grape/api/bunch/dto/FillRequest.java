package com.grape.api.bunch.dto;

import jakarta.validation.constraints.NotNull;

/** {@code PATCH /api/bunches/{id}/fill}. Out-of-range values are clamped by the server, not rejected. */
public record FillRequest(@NotNull Integer filled) {
}
