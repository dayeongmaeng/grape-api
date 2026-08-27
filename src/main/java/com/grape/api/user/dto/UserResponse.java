package com.grape.api.user.dto;

import com.grape.api.user.entity.User;

/** {@code GET /api/users/me} (§3-2). */
public record UserResponse(String id, String provider, String email, String nickname) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId().toString(),
                user.getProvider().name(),
                user.getEmail(),
                user.getNickname());
    }
}
