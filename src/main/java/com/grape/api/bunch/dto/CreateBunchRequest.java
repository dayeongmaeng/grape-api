package com.grape.api.bunch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** {@code POST /api/bunches}. {@code detail} is derived server-side, not sent by the client. */
public record CreateBunchRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String unitLabel,
        @NotNull @Positive Integer total,
        @NotNull @PositiveOrZero Integer periodDays) {
}
