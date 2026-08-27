package com.grape.api.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    /** NULL for guests. */
    @Column(name = "provider_user_id")
    private String providerUserId;

    @Column
    private String email;

    @Column
    private String nickname;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static User guest(Instant now) {
        User user = new User();
        user.provider = Provider.GUEST;
        user.createdAt = now;
        return user;
    }

    public static User social(Provider provider, String providerUserId, String email, String nickname, Instant now) {
        User user = new User();
        user.provider = provider;
        user.providerUserId = providerUserId;
        user.email = email;
        user.nickname = nickname;
        user.createdAt = now;
        return user;
    }

    /**
     * Guest-merge case A: this guest row becomes a real social account in place (same id, so
     * bunches/harvests keep pointing at it). createdAt is intentionally preserved. See §3-1.
     */
    public void convertGuestToSocial(Provider provider, String providerUserId, String email, String nickname) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
        this.nickname = nickname;
    }

    public boolean isGuest() {
        return provider == Provider.GUEST;
    }
}
