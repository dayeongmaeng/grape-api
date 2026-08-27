package com.grape.api.settings;

import com.grape.api.settings.dto.NotificationSettingsResponse;
import com.grape.api.settings.dto.UpdateSettingsRequest;
import com.grape.api.settings.entity.UserSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final UserSettingsRepository userSettingsRepository;

    @Transactional
    public NotificationSettingsResponse get(UUID userId) {
        return NotificationSettingsResponse.from(getOrCreate(userId));
    }

    @Transactional
    public NotificationSettingsResponse update(UUID userId, UpdateSettingsRequest request) {
        UserSettings settings = getOrCreate(userId);
        settings.apply(request.dailyReminder(), request.reminderTime(), request.fillSound());
        return NotificationSettingsResponse.from(settings);
    }

    /** Rows are created at signup; this is a safety net if one is ever missing. */
    private UserSettings getOrCreate(UUID userId) {
        return userSettingsRepository.findById(userId)
                .orElseGet(() -> userSettingsRepository.save(UserSettings.defaultsFor(userId)));
    }
}
