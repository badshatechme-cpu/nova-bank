package com.novabank.account.api.dto;

import com.novabank.account.domain.Transaction;
import com.novabank.account.domain.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A posted transaction on an account")
public record TransactionResponse(
        UUID id,
        UUID accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        BigDecimal balanceAfter,
        String narration,
        String counterparty,
        Instant bookedAt
) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getBalanceAfter(),
                transaction.getNarration(),
                transaction.getCounterparty(),
                transaction.getBookedAt());
    }
}
