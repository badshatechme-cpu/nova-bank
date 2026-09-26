CREATE TABLE transactions (
    id             UUID PRIMARY KEY,
    account_id     UUID NOT NULL REFERENCES accounts (id),
    type           VARCHAR(20) NOT NULL,
    amount         NUMERIC(19, 4) NOT NULL,
    currency       VARCHAR(3) NOT NULL,
    balance_after  NUMERIC(19, 4) NOT NULL,
    narration      VARCHAR(500) NOT NULL,
    counterparty   VARCHAR(200),
    booked_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_transactions_account_id_booked_at ON transactions (account_id, booked_at);
