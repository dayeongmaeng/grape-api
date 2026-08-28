package com.grape.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for {@code POST /api/auth/kakao/web} — the redirect authorization code and the exact redirect URI. */
public record KakaoCodeLoginRequest(@NotBlank String code, @NotBlank String redirectUri) {
}
