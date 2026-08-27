package com.grape.api.settings.dto;

import jakarta.validation.constraints.Size;

/** {@code PATCH /api/settings} — partial update. A null field means "leave unchanged". */
public record UpdateSettingsRequest(
        Boolean dailyReminder,
        @Size(max = 20) String reminderTime,
        Boolean fillSound) {
}
