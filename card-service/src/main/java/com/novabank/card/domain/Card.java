package com.novabank.card.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "cards")
public class Card {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "linked_account_id", nullable = false)
    private UUID linkedAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardScheme scheme;

    @Column(name = "masked_pan", nullable = false)
    private String maskedPan;

    @Column(name = "last4", nullable = false)
    private String last4;

    @Column(name = "expiry_month", nullable = false)
    private int expiryMonth;

    @Column(name = "expiry_year", nullable = false)
    private int expiryYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardStatus status;

    @Column(name = "daily_limit", nullable = false)
    private BigDecimal dailyLimit;

    @Column(nullable = false)
    private String currency;

    @Column(name = "block_reason")
    private String blockReason;

    protected Card() {
        // JPA
    }

    public Card(UUID id, UUID customerId, UUID linkedAccountId, CardType type, CardScheme scheme,
                String maskedPan, String last4, int expiryMonth, int expiryYear, CardStatus status,
                BigDecimal dailyLimit, String currency, String blockReason) {
        this.id = id;
        this.customerId = customerId;
        this.linkedAccountId = linkedAccountId;
        this.type = type;
        this.scheme = scheme;
        this.maskedPan = maskedPan;
        this.last4 = last4;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
        this.status = status;
        this.dailyLimit = dailyLimit;
        this.currency = currency;
        this.blockReason = blockReason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getLinkedAccountId() {
        return linkedAccountId;
    }

    public CardType getType() {
        return type;
    }

    public CardScheme getScheme() {
        return scheme;
    }

    public String getMaskedPan() {
        return maskedPan;
    }

    public String getLast4() {
        return last4;
    }

    public int getExpiryMonth() {
        return expiryMonth;
    }

    public int getExpiryYear() {
        return expiryYear;
    }

    public CardStatus getStatus() {
        return status;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public String getCurrency() {
        return currency;
    }

    public String getBlockReason() {
        return blockReason;
    }

    public void block(String reason) {
        if (status == CardStatus.BLOCKED) {
            throw new CardAlreadyBlockedException(id);
        }
        this.status = CardStatus.BLOCKED;
        this.blockReason = reason;
    }

    public void unblock() {
        if (status != CardStatus.BLOCKED) {
            throw new CardNotBlockedException(id);
        }
        this.status = CardStatus.ACTIVE;
        this.blockReason = null;
    }
}
