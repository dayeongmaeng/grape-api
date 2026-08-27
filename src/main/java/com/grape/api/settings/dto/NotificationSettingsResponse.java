package com.grape.api.settings.dto;

import com.grape.api.settings.entity.UserSettings;

/** Matches the client {@code NotificationSettings} type 1:1 (camelCase). */
public record NotificationSettingsResponse(
        boolean dailyReminder,
        String reminderTime,
        boolean fillSound) {

    public static NotificationSettingsResponse from(UserSettings settings) {
        return new NotificationSettingsResponse(
                settings.isDailyReminder(),
                settings.getReminderTime(),
                settings.isFillSound());
    }
}
