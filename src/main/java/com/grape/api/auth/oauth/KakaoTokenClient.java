package com.grape.api.auth.oauth;

/**
 * Exchanges the Kakao web login's authorization code (from {@code Kakao.Auth.authorize} redirect)
 * for a user access token, which is then resolved to an identity by {@link KakaoUserClient}.
 * Native login skips this — the client already holds the access token.
 */
public interface KakaoTokenClient {

    String exchangeCode(String code, String redirectUri);
}
