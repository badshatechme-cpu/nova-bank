package com.novabank.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "balance_after", nullable = false)
    private BigDecimal balanceAfter;

    @Column(nullable = false)
    private String narration;

    private String counterparty;

    @Column(name = "booked_at", nullable = false)
    private Instant bookedAt;

    protected Transaction() {
        // JPA
    }

    public Transaction(UUID id, UUID accountId, TransactionType type, BigDecimal amount, String currency,
                        BigDecimal balanceAfter, String narration, String counterparty, Instant bookedAt) {
        this.id = id;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.balanceAfter = balanceAfter;
        this.narration = narration;
        this.counterparty = counterparty;
        this.bookedAt = bookedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getNarration() {
        return narration;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public Instant getBookedAt() {
        return bookedAt;
    }
}
