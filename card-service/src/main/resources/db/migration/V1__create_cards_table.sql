CREATE TABLE cards (
    id                UUID PRIMARY KEY,
    customer_id       UUID NOT NULL,
    linked_account_id UUID NOT NULL,
    type              VARCHAR(20) NOT NULL,
    scheme            VARCHAR(20) NOT NULL,
    masked_pan        VARCHAR(24) NOT NULL,
    last4             VARCHAR(4) NOT NULL,
    expiry_month      INT NOT NULL,
    expiry_year       INT NOT NULL,
    status            VARCHAR(20) NOT NULL,
    daily_limit       NUMERIC(19, 4) NOT NULL,
    currency          VARCHAR(3) NOT NULL,
    block_reason      VARCHAR(200)
);

CREATE INDEX idx_cards_customer_id ON cards (customer_id);
