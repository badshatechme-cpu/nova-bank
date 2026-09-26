CREATE TABLE accounts (
    id             UUID PRIMARY KEY,
    customer_id    UUID NOT NULL,
    account_number VARCHAR(34) NOT NULL,
    iban           VARCHAR(34) NOT NULL,
    type           VARCHAR(20) NOT NULL,
    currency       VARCHAR(3) NOT NULL,
    balance        NUMERIC(19, 4) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    opened_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_accounts_account_number UNIQUE (account_number),
    CONSTRAINT uq_accounts_iban UNIQUE (iban)
);

CREATE INDEX idx_accounts_customer_id ON accounts (customer_id);
