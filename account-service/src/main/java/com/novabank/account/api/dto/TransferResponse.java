package com.novabank.account.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Result of a completed transfer")
public record TransferResponse(
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        String narration,
        UUID debitTransactionId,
        UUID creditTransactionId,
        Instant bookedAt
) {
}
