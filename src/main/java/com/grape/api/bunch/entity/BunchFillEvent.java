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
import java.time.LocalDate;
import java.util.UUID;

/**
 * One "fill increased" event. Append-only. Duplicate {@code fillDate} values for the same bunch
 * are allowed on purpose — never de-duplicate (see server-design-draft.md §3-3).
 * {@code Bunch.fillDates} is reconstructed as {@code SELECT fill_date ... ORDER BY created_at ASC}.
 */
@Entity
@Table(name = "bunch_fill_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BunchFillEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Plain column, not a @ManyToOne. Deletion is handled by the DB ON DELETE CASCADE. */
    @Column(name = "bunch_id", nullable = false)
    private UUID bunchId;

    @Column(name = "fill_date", nullable = false)
    private LocalDate fillDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static BunchFillEvent of(UUID bunchId, LocalDate fillDate, Instant createdAt) {
        BunchFillEvent event = new BunchFillEvent();
        event.bunchId = bunchId;
        event.fillDate = fillDate;
        event.createdAt = createdAt;
        return event;
    }
}
