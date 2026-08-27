package com.grape.api.user.entity;

/** Auth provider backing a {@link User}. Stored as its name() in users.provider. */
public enum Provider {
    GOOGLE,
    KAKAO,
    GUEST
}
