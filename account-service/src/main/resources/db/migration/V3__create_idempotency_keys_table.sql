CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    customer_id     UUID NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    response_body   TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL
);
