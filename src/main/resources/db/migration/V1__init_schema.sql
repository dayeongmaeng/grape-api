-- Initial schema. Mirrors server-design-draft.md §6 (PostgreSQL).

CREATE TABLE users (
    id               UUID          PRIMARY KEY,
    provider         VARCHAR(20)   NOT NULL,          -- 'GOOGLE' | 'KAKAO' | 'GUEST'
    provider_user_id VARCHAR(255),                    -- NULL for guests
    email            VARCHAR(255),
    nickname         VARCHAR(100),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- guests all have provider_user_id = NULL; Postgres treats NULLs as distinct, so no collision
    CONSTRAINT uq_users_provider_provider_user_id UNIQUE (provider, provider_user_id)
);

CREATE TABLE refresh_tokens (
    id         UUID          PRIMARY KEY,
    user_id    UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255)  NOT NULL,                -- hash only, never the raw token
    expires_at TIMESTAMPTZ   NOT NULL,                -- issued_at + 30d
    revoked_at TIMESTAMPTZ,                           -- set on logout / rotation; NULL = still valid
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);

CREATE TABLE bunches (
    id           UUID          PRIMARY KEY,
    user_id      UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name         VARCHAR(100)  NOT NULL,
    detail       VARCHAR(255)  NOT NULL DEFAULT '',
    unit_label   VARCHAR(100)  NOT NULL DEFAULT '',
    total        INTEGER       NOT NULL,
    filled       INTEGER       NOT NULL DEFAULT 0,
    period_days  INTEGER       NOT NULL DEFAULT 0,    -- 0 = no period
    created_at   TIMESTAMPTZ   NOT NULL,
    completed_at TIMESTAMPTZ,
    completions  INTEGER       NOT NULL DEFAULT 0
);
CREATE INDEX idx_bunches_user_id ON bunches (user_id);

-- Bunch.fillDates[] normalised as an append-only log. Duplicate fill_date rows are allowed on
-- purpose (see §3-3 / "하지 말아야 할 것"): never de-duplicate.
CREATE TABLE bunch_fill_events (
    id         BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bunch_id   UUID          NOT NULL REFERENCES bunches (id) ON DELETE CASCADE,
    fill_date  DATE          NOT NULL,                -- YYYY-MM-DD
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()   -- ordering only, not exposed in responses
);
CREATE INDEX idx_bunch_fill_events_bunch_id_created_at ON bunch_fill_events (bunch_id, created_at);

CREATE TABLE harvests (
    id              UUID          PRIMARY KEY,
    user_id         UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- No FK constraint on purpose: the source bunch may be deleted and this value must survive
    -- (orphan reference allowed). Type kept identical to bunches.id. See §6.
    source_bunch_id UUID          NOT NULL,
    name            VARCHAR(100)  NOT NULL,
    count           INTEGER       NOT NULL,
    harvested_at    TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_harvests_user_id ON harvests (user_id);

-- 1:1 with users
CREATE TABLE user_settings (
    user_id        UUID         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    daily_reminder BOOLEAN      NOT NULL DEFAULT true,
    reminder_time  VARCHAR(20)  NOT NULL DEFAULT '저녁 9:00',   -- free string, not a LocalTime (§4/§5)
    fill_sound     BOOLEAN      NOT NULL DEFAULT true
);
