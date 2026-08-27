package com.grape.api.auth;

import com.grape.api.auth.entity.RefreshToken;
import com.grape.api.common.ApiException;
import com.grape.api.common.ErrorCode;
import com.grape.api.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque refresh tokens with server-side revocation. The raw token is returned to the caller once;
 * only its SHA-256 hash is stored. See server-design-draft.md §3-1.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final AppProperties props;

    public record Rotation(UUID userId, String rawRefreshToken) {
    }

    /** Creates a new refresh token for the user and returns the raw value. */
    @Transactional
    public String issue(UUID userId, Instant now) {
        String raw = randomToken();
        repository.save(RefreshToken.issue(userId, hash(raw), now, now.plus(props.jwt().refreshTtl())));
        return raw;
    }

    /**
     * Validates the presented token, revokes it, and issues a replacement (rotation).
     *
     * @throws ApiException {@code INVALID_REFRESH_TOKEN} if unknown, expired or already revoked.
     */
    @Transactional
    public Rotation rotate(String rawToken, Instant now) {
        RefreshToken current = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (!current.isActiveAt(now)) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        current.revoke(now);
        String raw = randomToken();
        repository.save(RefreshToken.issue(current.getUserId(), hash(raw), now, now.plus(props.jwt().refreshTtl())));
        return new Rotation(current.getUserId(), raw);
    }

    /** Best-effort revoke for logout: only acts if the token exists and belongs to {@code userId}. */
    @Transactional
    public void revoke(String rawToken, UUID userId, Instant now) {
        repository.findByTokenHash(hash(rawToken))
                .filter(token -> token.getUserId().equals(userId))
                .ifPresent(token -> token.revoke(now));
    }

    private static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
