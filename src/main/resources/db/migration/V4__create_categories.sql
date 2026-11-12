CREATE TABLE categories (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users (id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_categories_default_not_archived CHECK (user_id IS NOT NULL OR archived_at IS NULL)
);

CREATE INDEX idx_categories_user_id ON categories (user_id);

CREATE UNIQUE INDEX uk_categories_default_name
    ON categories (type, LOWER(name))
    WHERE user_id IS NULL;

CREATE UNIQUE INDEX uk_categories_user_active_name
    ON categories (user_id, type, LOWER(name))
    WHERE user_id IS NOT NULL AND archived_at IS NULL;

INSERT INTO categories (id, user_id, name, type) VALUES
    (gen_random_uuid(), NULL, 'Alimentação', 'EXPENSE'),
    (gen_random_uuid(), NULL, 'Moradia', 'EXPENSE'),
    (gen_random_uuid(), NULL, 'Transporte', 'EXPENSE'),
    (gen_random_uuid(), NULL, 'Saúde', 'EXPENSE'),
    (gen_random_uuid(), NULL, 'Educação', 'EXPENSE'),
    (gen_random_uuid(), NULL, 'Lazer', 'EXPENSE'),
    (gen_random_uuid(), NULL, 'Compras', 'EXPENSE'),
    (gen_random_uuid(), NULL, 'Contas e serviços', 'EXPENSE'),
    (gen_random_uuid(), NULL, 'Outras despesas', 'EXPENSE'),
    (gen_random_uuid(), NULL, 'Salário', 'INCOME'),
    (gen_random_uuid(), NULL, 'Freelance', 'INCOME'),
    (gen_random_uuid(), NULL, 'Investimentos', 'INCOME'),
    (gen_random_uuid(), NULL, 'Outras receitas', 'INCOME');
