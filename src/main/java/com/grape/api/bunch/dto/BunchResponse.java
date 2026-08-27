package com.grape.api.bunch.dto;

import com.grape.api.bunch.entity.Bunch;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Matches the client {@code Bunch} type 1:1 (camelCase). {@code completedAt} is serialised as
 * {@code null} when not completed (Jackson default). {@code fillDates} are {@code YYYY-MM-DD},
 * oldest first, with duplicates kept.
 */
public record BunchResponse(
        UUID id,
        String name,
        String detail,
        String unitLabel,
        int total,
        int filled,
        int periodDays,
        Instant createdAt,
        List<LocalDate> fillDates,
        Instant completedAt,
        int completions) {

    public static BunchResponse of(Bunch bunch, List<LocalDate> fillDates) {
        return new BunchResponse(
                bunch.getId(),
                bunch.getName(),
                bunch.getDetail(),
                bunch.getUnitLabel(),
                bunch.getTotal(),
                bunch.getFilled(),
                bunch.getPeriodDays(),
                bunch.getCreatedAt(),
                fillDates,
                bunch.getCompletedAt(),
                bunch.getCompletions());
    }
}
