package com.grape.api.auth.oauth;

/** Verified identity from a social provider. {@code email}/{@code nickname} may be null. */
public record OAuthUserInfo(String providerUserId, String email, String nickname) {
}
