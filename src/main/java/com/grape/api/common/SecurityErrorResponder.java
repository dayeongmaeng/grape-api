package com.grape.api.common;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes the uniform {@code {code, message}} body for failures raised inside the Spring Security
 * filter chain (before the {@code @RestControllerAdvice} can see them). Only {@link ErrorCode}
 * constants are emitted, whose names and default messages are plain ASCII — safe to inline.
 */
public final class SecurityErrorResponder {

    private SecurityErrorResponder() {
    }

    public static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"code\":\"" + errorCode.name() + "\",\"message\":\"" + errorCode.getDefaultMessage() + "\"}");
    }
}
