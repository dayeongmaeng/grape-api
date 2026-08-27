package com.grape.api.auth.oauth;

/** Verifies a Kakao access token by calling the Kakao API and returns the identity it resolves to. */
public interface KakaoUserClient {

    OAuthUserInfo fetchUser(String kakaoAccessToken);
}
