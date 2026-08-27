package com.grape.api.settings;

import com.grape.api.settings.dto.NotificationSettingsResponse;
import com.grape.api.settings.dto.UpdateSettingsRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public NotificationSettingsResponse get(@AuthenticationPrincipal UUID userId) {
        return settingsService.get(userId);
    }

    @PatchMapping
    public NotificationSettingsResponse update(@AuthenticationPrincipal UUID userId,
                                               @Valid @RequestBody UpdateSettingsRequest request) {
        return settingsService.update(userId, request);
    }
}
