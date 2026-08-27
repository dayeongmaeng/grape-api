package com.grape.api.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** 1:1 with users. PK is the user id (no separate surrogate key). */
@Entity
@Table(name = "user_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "daily_reminder", nullable = false)
    private boolean dailyReminder;

    /** Free string (e.g. "저녁 9:00"), not a LocalTime. */
    @Column(name = "reminder_time", nullable = false, length = 20)
    private String reminderTime;

    @Column(name = "fill_sound", nullable = false)
    private boolean fillSound;

    /** Defaults per server-design-draft.md §6 — must stay in sync with V1__init_schema.sql. */
    public static UserSettings defaultsFor(UUID userId) {
        UserSettings settings = new UserSettings();
        settings.userId = userId;
        settings.dailyReminder = true;
        settings.reminderTime = "저녁 9:00";
        settings.fillSound = true;
        return settings;
    }

    /** PATCH /api/settings (§3-5): partial update — only non-null fields are applied. */
    public void apply(Boolean dailyReminder, String reminderTime, Boolean fillSound) {
        if (dailyReminder != null) {
            this.dailyReminder = dailyReminder;
        }
        if (reminderTime != null) {
            this.reminderTime = reminderTime;
        }
        if (fillSound != null) {
            this.fillSound = fillSound;
        }
    }
}
