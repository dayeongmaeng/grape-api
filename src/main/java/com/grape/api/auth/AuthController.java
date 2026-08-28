package com.grape.api.auth;

import com.grape.api.auth.dto.GoogleLoginRequest;
import com.grape.api.auth.dto.KakaoCodeLoginRequest;
import com.grape.api.auth.dto.KakaoLoginRequest;
import com.grape.api.auth.dto.LoginResponse;
import com.grape.api.auth.dto.LogoutRequest;
import com.grape.api.auth.dto.RefreshRequest;
import com.grape.api.auth.dto.RefreshResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Optional-auth: an {@code Authorization: Bearer <guestAccessToken>} triggers guest merge (§3-1). */
    @PostMapping("/google")
    public LoginResponse google(@Valid @RequestBody GoogleLoginRequest request,
                                @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return authService.loginWithGoogle(request.idToken(), authorization);
    }

    /** Optional-auth: see {@link #google}. Native login — the client already holds the Kakao access token. */
    @PostMapping("/kakao")
    public LoginResponse kakao(@Valid @RequestBody KakaoLoginRequest request,
                               @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return authService.loginWithKakao(request.accessToken(), authorization);
    }

    /**
     * Optional-auth: see {@link #google}. Web login — the browser can only obtain an authorization code
     * ({@code Kakao.Auth.authorize} redirect), which the server exchanges for an access token.
     */
    @PostMapping("/kakao/web")
    public LoginResponse kakaoWeb(@Valid @RequestBody KakaoCodeLoginRequest request,
                                  @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return authService.loginWithKakaoCode(request.code(), request.redirectUri(), authorization);
    }

    @PostMapping("/guest")
    public LoginResponse guest() {
        return authService.guestLogin();
    }

    @PostMapping("/refresh")
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request,
                       @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(authorization, request.refreshToken());
    }
}
