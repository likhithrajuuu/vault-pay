-- Wallet Service owns this table exclusively.
-- UUIDs are supplied by the application; no database extension is required.
-- Wallet holds identity and lifecycle metadata only. Balances and postings belong
-- to Transfer & Ledger (ADR-02), so there is deliberately no balance column here.

CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    -- Identity Service's user id (the JWT subject). No foreign key: that table lives
    -- in another service's database and cross-service constraints are not allowed.
    owner_user_id UUID NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    -- One wallet per user in the first release. This also makes creation idempotent.
    CONSTRAINT uq_wallets_owner UNIQUE (owner_user_id),
    CONSTRAINT ck_wallets_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_wallets_status CHECK (
        status IN ('PENDING', 'ACTIVE', 'FROZEN', 'CLOSED')
    ),
    CONSTRAINT ck_wallets_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_wallets_version CHECK (version >= 0)
);

COMMENT ON TABLE wallets IS
    'Wallet metadata and requested lifecycle status. Not a balance or posting store.';
COMMENT ON COLUMN wallets.owner_user_id IS
    'Immutable in the first release. Taken from the verified token subject, never from a request body.';
COMMENT ON COLUMN wallets.status IS
    'Requested lifecycle status. PENDING means the financial account is not yet confirmed; it is not spendable.';
COMMENT ON COLUMN wallets.version IS
    'Optimistic concurrency counter. Updates must compare and increment it atomically; no trigger is installed.';
COMMENT ON COLUMN wallets.updated_at IS
    'Defaults only on insert. Persistence code must set it on every update.';
