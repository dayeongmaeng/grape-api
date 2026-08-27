package com.grape.api.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Stable error identifiers returned as {@code {"code": <name()>, "message": ...}}.
 * {@code code} = the enum constant name (UPPER_SNAKE); clients branch on it. {@code message} is a
 * concise developer-facing English string and is not part of the API contract.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_GOOGLE_TOKEN(HttpStatus.UNAUTHORIZED, "Google ID token could not be verified"),
    INVALID_KAKAO_TOKEN(HttpStatus.UNAUTHORIZED, "Kakao access token could not be verified"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh token is expired, revoked, or unknown"),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");

    private final HttpStatus status;
    private final String defaultMessage;
}
