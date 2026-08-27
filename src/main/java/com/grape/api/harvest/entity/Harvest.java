package com.grape.api.harvest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Intentionally reduced snapshot of a bunch at harvest time (not a subset view). */
@Entity
@Table(name = "harvests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Harvest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Plain value column — deliberately NOT a @ManyToOne / FK.
     * The source bunch may be deleted; this id is kept as-is (orphan reference allowed).
     * See server-design-draft.md §6 and AGENTS.md "하지 말아야 할 것".
     */
    @Column(name = "source_bunch_id", nullable = false)
    private UUID sourceBunchId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int count;

    @Column(name = "harvested_at", nullable = false)
    private Instant harvestedAt;

    /** Reduced snapshot taken on replant / archive (§3-3). */
    public static Harvest snapshot(UUID userId, UUID sourceBunchId, String name, int count, Instant harvestedAt) {
        Harvest harvest = new Harvest();
        harvest.userId = userId;
        harvest.sourceBunchId = sourceBunchId;
        harvest.name = name;
        harvest.count = count;
        harvest.harvestedAt = harvestedAt;
        return harvest;
    }
}
