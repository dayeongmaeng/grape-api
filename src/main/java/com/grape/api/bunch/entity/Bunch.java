package com.grape.api.bunch.entity;

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

@Entity
@Table(name = "bunches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bunch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    /** Derived from unitLabel by the server, not a client input. */
    @Column(nullable = false, length = 255)
    private String detail;

    @Column(name = "unit_label", nullable = false, length = 100)
    private String unitLabel;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int filled;

    /** 0 = no period. */
    @Column(name = "period_days", nullable = false)
    private int periodDays;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(nullable = false)
    private int completions;

    /** {@code POST /api/bunches} (§3-3). detail is derived here with the same formula as the client. */
    public static Bunch create(UUID userId, String name, String unitLabel, int total, int periodDays, Instant now) {
        Bunch bunch = new Bunch();
        bunch.userId = userId;
        bunch.name = name;
        bunch.unitLabel = unitLabel == null ? "" : unitLabel;
        bunch.detail = bunch.unitLabel.isEmpty() ? "" : "한 알 = " + bunch.unitLabel;
        bunch.total = total;
        bunch.filled = 0;
        bunch.periodDays = periodDays;
        bunch.createdAt = now;
        bunch.completions = 0;
        return bunch;
    }

    /**
     * {@code POST /api/harvests/{id}/recall} (§3-4): a brand-new active bunch built from a harvest
     * snapshot. New id (the original sourceBunchId is never reused). Fields absent from the snapshot
     * are explicit empty values; completedAt is left null (not in the §3-4 field list).
     */
    public static Bunch fromRecall(UUID userId, String name, int total, int filled, Instant now) {
        Bunch bunch = new Bunch();
        bunch.userId = userId;
        bunch.name = name;
        bunch.detail = "";
        bunch.unitLabel = "";
        bunch.total = total;
        bunch.filled = filled;
        bunch.periodDays = 0;
        bunch.createdAt = now;
        bunch.completions = 0;
        return bunch;
    }

    /**
     * {@code PATCH /api/bunches/{id}/fill} (§3-3). Clamp only — no extra gating.
     *
     * @return true if {@code filled} increased, in which case the caller appends a fill_date event
     * for "today" (Asia/Seoul). Duplicates across calls are kept on purpose.
     */
    public boolean applyFill(int requestedFilled, Instant now) {
        int clamped = Math.max(0, Math.min(total, requestedFilled));
        boolean increased = clamped > this.filled;
        this.filled = clamped;
        if (clamped == total) {
            if (this.completedAt == null) {
                this.completedAt = now; // first time reaching total
            }
            // already completed -> keep the original timestamp
        } else {
            this.completedAt = null; // dropped back below total -> un-complete
        }
        return increased;
    }

    /**
     * {@code POST /api/bunches/{id}/replant} (§3-3): keep this row, start a new cycle.
     * bunch_fill_events are intentionally NOT touched (fillDates accumulate across cycles).
     */
    public void resetForReplant(Instant now) {
        this.filled = 0;
        this.completedAt = null;
        this.createdAt = now;
        this.completions += 1;
    }
}
