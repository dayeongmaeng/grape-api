package com.grape.api.auth.oauth;

/** Verifies a Google ID token and returns the identity it asserts. */
public interface GoogleTokenVerifier {

    OAuthUserInfo verify(String idToken);
}
