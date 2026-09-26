package com.novabank.account.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Request body to transfer funds between two accounts")
public record TransferRequest(

        @NotNull
        UUID fromAccountId,

        @NotNull
        UUID toAccountId,

        @NotNull
        @DecimalMin(value = "0.01")
        @Schema(example = "100.00")
        BigDecimal amount,

        @NotBlank
        @Size(min = 3, max = 3)
        @Schema(description = "ISO 4217 currency code", example = "AED")
        String currency,

        @NotBlank
        @Size(max = 500)
        @Schema(example = "Rent payment")
        String narration,

        @Schema(description = "Optional booking timestamp, for backdating synthetic/imported history. Defaults to now.")
        Instant bookedAt
) {
    public TransferRequest(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String currency, String narration) {
        this(fromAccountId, toAccountId, amount, currency, narration, null);
    }
}
