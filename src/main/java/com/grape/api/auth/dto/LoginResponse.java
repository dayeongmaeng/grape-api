package com.grape.api.auth.dto;

/** Response for {@code POST /api/auth/google|kakao|guest}. */
public record LoginResponse(String accessToken, String refreshToken, AuthUserResponse user) {
}
