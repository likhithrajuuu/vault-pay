-- PostgreSQL migration definition only. This file has not been executed.
-- UUIDs are supplied by the application; no database extension is required.
-- These tables belong exclusively to Identity Service.

CREATE TABLE identity_users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    phone_number VARCHAR(16),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified_at TIMESTAMPTZ,
    phone_verified_at TIMESTAMPTZ,

    address_line_1 VARCHAR(200),
    address_line_2 VARCHAR(200),
    address_city VARCHAR(100),
    address_state VARCHAR(100),
    address_postal_code VARCHAR(20),
    address_country_code VARCHAR(2),

    pan_ciphertext BYTEA,
    pan_encryption_key_id VARCHAR(255),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_identity_users_email UNIQUE (email),
    CONSTRAINT ck_identity_users_email CHECK (
        email = lower(btrim(email))
        AND email ~ '^[^[:space:]@]+@[^[:space:]@]+$'
    ),
    CONSTRAINT ck_identity_users_full_name CHECK (
        char_length(btrim(full_name)) > 0
    ),
    CONSTRAINT ck_identity_users_phone CHECK (
        phone_number IS NULL OR phone_number ~ '^\+[1-9][0-9]{1,14}$'
    ),
    CONSTRAINT ck_identity_users_status CHECK (
        status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'CLOSED')
    ),
    CONSTRAINT ck_identity_users_phone_verification CHECK (
        phone_verified_at IS NULL OR phone_number IS NOT NULL
    ),
    CONSTRAINT ck_identity_users_address CHECK (
        (
            address_line_1 IS NULL AND address_line_2 IS NULL
            AND address_city IS NULL AND address_state IS NULL
            AND address_postal_code IS NULL AND address_country_code IS NULL
        ) OR (
            address_line_1 IS NOT NULL AND char_length(btrim(address_line_1)) > 0
            AND address_city IS NOT NULL AND char_length(btrim(address_city)) > 0
            AND address_state IS NOT NULL AND char_length(btrim(address_state)) > 0
            AND address_postal_code IS NOT NULL AND char_length(btrim(address_postal_code)) > 0
            AND address_country_code IS NOT NULL AND address_country_code ~ '^[A-Z]{2}$'
            AND (address_line_2 IS NULL OR char_length(btrim(address_line_2)) > 0)
        )
    ),
    CONSTRAINT ck_identity_users_pan CHECK (
        (pan_ciphertext IS NULL AND pan_encryption_key_id IS NULL)
        OR (
            pan_ciphertext IS NOT NULL AND octet_length(pan_ciphertext) > 0
            AND pan_encryption_key_id IS NOT NULL
            AND char_length(btrim(pan_encryption_key_id)) > 0
        )
    ),
    CONSTRAINT ck_identity_users_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_identity_users_version CHECK (version >= 0)
);

-- Unverified numbers do not reserve ownership of a phone number.
CREATE UNIQUE INDEX uq_identity_users_verified_phone
    ON identity_users (phone_number)
    WHERE phone_verified_at IS NOT NULL;

COMMENT ON TABLE identity_users IS
    'Identity-owned account/profile data. Not a wallet, balance, or JWT store.';
COMMENT ON COLUMN identity_users.email IS
    'Application policy: canonical lowercase email; verification is a separate workflow.';
COMMENT ON COLUMN identity_users.pan_ciphertext IS
    'Authenticated encrypted envelope containing PAN; never plaintext. Encryption and plaintext format validation happen before persistence.';
COMMENT ON COLUMN identity_users.pan_encryption_key_id IS
    'Reference to externally managed encryption key material, not the key itself. PAN collection does not establish KYC verification.';
COMMENT ON COLUMN identity_users.version IS
    'Optimistic concurrency counter. Future persistence updates must compare and increment it atomically; no trigger is installed.';
COMMENT ON COLUMN identity_users.updated_at IS
    'Defaults only on insert. Future persistence code must update this timestamp on changes and reset verification when email/phone changes.';

CREATE TABLE user_password_credentials (
    user_id UUID PRIMARY KEY REFERENCES identity_users (id) ON DELETE RESTRICT,
    password_hash VARCHAR(1024) NOT NULL,
    password_changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_user_password_hash_format CHECK (
        password_hash ~ '^\$argon2id\$v=19\$m=[1-9][0-9]*,t=[1-9][0-9]*,p=[1-9][0-9]*\$[A-Za-z0-9+/]+\$[A-Za-z0-9+/]+$'
    ),
    CONSTRAINT ck_user_password_hash_cost CHECK (
        substring(password_hash FROM 'm=([0-9]+),')::NUMERIC >= 19456
        AND substring(password_hash FROM ',t=([0-9]+),')::NUMERIC >= 2
        AND substring(password_hash FROM ',p=([0-9]+)\$')::NUMERIC >= 1
    ),
    CONSTRAINT ck_user_password_timestamps CHECK (
        updated_at >= created_at
        AND password_changed_at >= created_at
        AND password_changed_at <= updated_at
    ),
    CONSTRAINT ck_user_password_version CHECK (version >= 0)
);

COMMENT ON TABLE user_password_credentials IS
    'Optional local credential: OAuth-only users have no row. Stores only an Argon2id PHC string with its salt and cost parameters, never a raw password.';
COMMENT ON COLUMN user_password_credentials.password_hash IS
    'Bare PHC encoding without a Spring DelegatingPasswordEncoder prefix. Baseline m>=19456 KiB, t>=2, p>=1; hashing/verification require a real Argon2id encoder and later benchmarking.';

CREATE TABLE user_oauth_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES identity_users (id) ON DELETE RESTRICT,
    provider VARCHAR(10) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_user_oauth_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT ck_user_oauth_provider CHECK (provider IN ('GOOGLE', 'APPLE')),
    CONSTRAINT ck_user_oauth_subject CHECK (
        char_length(btrim(provider_subject)) > 0
    )
);

CREATE INDEX ix_user_oauth_identities_user_id ON user_oauth_identities (user_id);

COMMENT ON TABLE user_oauth_identities IS
    'Provider enum maps to a trusted issuer configuration. Subject comes from verified OIDC claims; do not link accounts solely by email. Provider tokens are not stored here.';

-- RS256 is a future token signing/verification policy, not a user column.
-- Keep RSA private keys outside these tables/source control; use a key ID (kid)
-- and externally managed signing material. Never store signed JWTs as passwords.
