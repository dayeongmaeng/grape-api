package com.grape.api.auth.dto;

/** Response for {@code POST /api/auth/refresh}. */
public record RefreshResponse(String accessToken, String refreshToken) {
}
