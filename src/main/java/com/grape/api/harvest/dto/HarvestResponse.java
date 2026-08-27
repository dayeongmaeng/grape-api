package com.grape.api.harvest.dto;

import com.grape.api.harvest.entity.Harvest;

import java.time.Instant;
import java.util.UUID;

/** Matches the client {@code Harvest} type 1:1 (camelCase). */
public record HarvestResponse(
        UUID id,
        UUID sourceBunchId,
        String name,
        int count,
        Instant harvestedAt) {

    public static HarvestResponse from(Harvest harvest) {
        return new HarvestResponse(
                harvest.getId(),
                harvest.getSourceBunchId(),
                harvest.getName(),
                harvest.getCount(),
                harvest.getHarvestedAt());
    }
}
