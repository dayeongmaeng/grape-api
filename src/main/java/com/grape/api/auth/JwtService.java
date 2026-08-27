package com.grape.api.auth;

import com.grape.api.common.ApiException;
import com.grape.api.common.ErrorCode;
import com.grape.api.common.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** Issues and verifies the stateless access token (HMAC-SHA256 JWT). Subject = user id. */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTtl;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = props.jwt().accessTtl();
        this.clock = clock;
    }

    public String issueAccessToken(UUID userId, Instant now) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key, Jwts.SIG.HS256) // pinned, not length-derived
                .compact();
    }

    /**
     * @return the user id carried by a valid, unexpired token (expiry evaluated against the
     * injected {@link Clock}).
     * @throws ApiException {@code AUTH_REQUIRED} if the token is malformed, badly signed or expired.
     */
    public UUID parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED, "Access token is invalid or expired");
        }
    }
}
