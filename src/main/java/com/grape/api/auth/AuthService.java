package com.grape.api.auth;

import com.grape.api.auth.dto.AuthUserResponse;
import com.grape.api.auth.dto.LoginResponse;
import com.grape.api.auth.dto.RefreshResponse;
import com.grape.api.auth.oauth.GoogleTokenVerifier;
import com.grape.api.auth.oauth.KakaoUserClient;
import com.grape.api.auth.oauth.OAuthUserInfo;
import com.grape.api.bunch.BunchRepository;
import com.grape.api.common.ApiException;
import com.grape.api.common.ErrorCode;
import com.grape.api.harvest.HarvestRepository;
import com.grape.api.settings.UserSettingsRepository;
import com.grape.api.settings.entity.UserSettings;
import com.grape.api.user.UserRepository;
import com.grape.api.user.entity.Provider;
import com.grape.api.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final BunchRepository bunchRepository;
    private final HarvestRepository harvestRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final KakaoUserClient kakaoUserClient;
    private final Clock clock;

    @Transactional
    public LoginResponse loginWithGoogle(String idToken, String authorizationHeader) {
        OAuthUserInfo identity = googleTokenVerifier.verify(idToken);
        return socialLogin(Provider.GOOGLE, identity, authorizationHeader);
    }

    @Transactional
    public LoginResponse loginWithKakao(String kakaoAccessToken, String authorizationHeader) {
        OAuthUserInfo identity = kakaoUserClient.fetchUser(kakaoAccessToken);
        return socialLogin(Provider.KAKAO, identity, authorizationHeader);
    }

    @Transactional
    public LoginResponse guestLogin() {
        Instant now = clock.instant();
        User guest = createUser(User.guest(now));
        return issueLogin(guest, now);
    }

    @Transactional
    public RefreshResponse refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken, now);
        String accessToken = jwtService.issueAccessToken(rotation.userId(), now);
        return new RefreshResponse(accessToken, rotation.rawRefreshToken());
    }

    @Transactional
    public void logout(String authorizationHeader, String rawRefreshToken) {
        UUID userId = requireUserId(authorizationHeader);
        refreshTokenService.revoke(rawRefreshToken, userId, clock.instant());
    }

    // --- social login + guest merge (server-design-draft.md §3-1) ---------------------------------

    private LoginResponse socialLogin(Provider provider, OAuthUserInfo identity, String authorizationHeader) {
        Instant now = clock.instant();
        Optional<User> existing = userRepository.findByProviderAndProviderUserId(provider, identity.providerUserId());
        Optional<User> guest = resolveGuest(authorizationHeader);

        User account;
        if (guest.isPresent()) {
            account = mergeGuest(guest.get(), existing, provider, identity);
        } else {
            account = existing.orElseGet(() -> createUser(
                    User.social(provider, identity.providerUserId(), identity.email(), identity.nickname(), now)));
        }
        return issueLogin(account, now);
    }

    private User mergeGuest(User guest, Optional<User> existing, Provider provider, OAuthUserInfo identity) {
        if (existing.isEmpty()) {
            // Case A — no account for this provider_user_id: promote the guest row in place.
            // Same user_id, so bunches/harvests need no migration.
            guest.convertGuestToSocial(provider, identity.providerUserId(), identity.email(), identity.nickname());
            return guest;
        }
        // Case B — an account already exists (signed up first on another device): move the guest's
        // data to it, then delete the guest row. The guest's user_settings + refresh_tokens go with
        // it via ON DELETE CASCADE. Response tokens belong to the existing (target) account.
        User target = existing.get();
        bunchRepository.reassignOwner(guest.getId(), target.getId());
        harvestRepository.reassignOwner(guest.getId(), target.getId());
        userRepository.delete(guest);
        userRepository.flush();
        return target;
    }

    private Optional<User> resolveGuest(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return Optional.empty();
        }
        UUID userId;
        try {
            userId = jwtService.parseUserId(token);
        } catch (ApiException ex) {
            return Optional.empty(); // invalid/expired guest token -> behave as if no header was sent
        }
        return userRepository.findById(userId).filter(User::isGuest);
    }

    private User createUser(User user) {
        User saved = userRepository.save(user);
        userSettingsRepository.save(UserSettings.defaultsFor(saved.getId()));
        return saved;
    }

    private LoginResponse issueLogin(User account, Instant now) {
        String accessToken = jwtService.issueAccessToken(account.getId(), now);
        String refreshToken = refreshTokenService.issue(account.getId(), now);
        return new LoginResponse(accessToken, refreshToken, AuthUserResponse.from(account));
    }

    private UUID requireUserId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED);
        }
        return jwtService.parseUserId(authorizationHeader.substring(BEARER_PREFIX.length()).trim());
    }
}
