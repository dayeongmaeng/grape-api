package com.grape.api.common;

import lombok.Getter;

/** Application error carrying an {@link ErrorCode} (which fixes the HTTP status and the {@code code}). */
@Getter
public class ApiException extends RuntimeException {

    private final transient ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
