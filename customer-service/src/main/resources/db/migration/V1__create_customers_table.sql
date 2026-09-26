CREATE TABLE customers (
    id             UUID PRIMARY KEY,
    full_name      VARCHAR(200) NOT NULL,
    email          VARCHAR(320) NOT NULL,
    mobile         VARCHAR(20) NOT NULL,
    date_of_birth  DATE NOT NULL,
    nationality    VARCHAR(3) NOT NULL,
    kyc_status     VARCHAR(20) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_customers_email UNIQUE (email)
);

CREATE INDEX idx_customers_full_name ON customers (full_name);
