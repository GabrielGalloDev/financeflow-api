ALTER TABLE accounts ADD CONSTRAINT uk_accounts_id_user UNIQUE (id, user_id);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id UUID NOT NULL,
    category_id UUID REFERENCES categories (id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER_IN', 'TRANSFER_OUT')),
    status VARCHAR(10) NOT NULL CHECK (status IN ('PAID', 'PENDING')),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    date DATE NOT NULL,
    description VARCHAR(140) NOT NULL,
    notes VARCHAR(500),
    transfer_group_id UUID,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_transactions_account_owner
        FOREIGN KEY (account_id, user_id) REFERENCES accounts (id, user_id),

    CONSTRAINT ck_transactions_category_or_transfer CHECK (
        (type IN ('INCOME', 'EXPENSE') AND category_id IS NOT NULL AND transfer_group_id IS NULL)
        OR
        (type IN ('TRANSFER_IN', 'TRANSFER_OUT') AND category_id IS NULL AND transfer_group_id IS NOT NULL)
    ),

    CONSTRAINT ck_transactions_transfer_paid CHECK (
        type NOT IN ('TRANSFER_IN', 'TRANSFER_OUT') OR status = 'PAID'
    )
);

CREATE INDEX idx_transactions_user_date
    ON transactions (user_id, date DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_transactions_account_id ON transactions (account_id);

CREATE INDEX idx_transactions_category_id ON transactions (category_id);

CREATE INDEX idx_transactions_transfer_group_id
    ON transactions (transfer_group_id)
    WHERE transfer_group_id IS NOT NULL;
