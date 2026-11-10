CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE CHECK(email = LOWER(email)),
    password_hash VARCHAR(100) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',
    timezone VARCHAR(50) NOT NULL DEFAULT 'America/Sao_Paulo',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);