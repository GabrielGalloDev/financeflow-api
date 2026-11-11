CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('WALLET', 'CHECKING', 'SAVINGS', 'CREDIT_CARD')),
    institution VARCHAR(100),
    initial_balance NUMERIC(19, 2) NOT NULL DEFAULT 0,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_user_id ON accounts (user_id);

CREATE UNIQUE INDEX uk_accounts_user_active_name
    ON accounts (user_id, LOWER(name))
    WHERE archived_at IS NULL;
