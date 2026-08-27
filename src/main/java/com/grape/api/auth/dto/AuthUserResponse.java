package com.grape.api.auth.dto;

import com.grape.api.user.entity.User;

/** {@code user} object embedded in login responses. Matches client {@code user} shape (§3-1). */
public record AuthUserResponse(String id, String provider, String email, String nickname) {

    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(
                user.getId().toString(),
                user.getProvider().name(),
                user.getEmail(),
                user.getNickname());
    }
}
