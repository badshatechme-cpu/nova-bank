package com.novabank.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
        // JPA
    }

    public IdempotencyRecord(String idempotencyKey, UUID customerId, String requestHash,
                              String responseBody, Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.customerId = customerId;
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
