package com.novabank.card.api.dto;

import com.novabank.card.domain.CardScheme;
import com.novabank.card.domain.CardType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Request body to issue a new card. A masked PAN is fabricated server-side; "
        + "the full PAN is never accepted, stored, or returned.")
public record CreateCardRequest(

        @NotNull
        UUID linkedAccountId,

        @NotNull
        CardType type,

        @NotNull
        CardScheme scheme,

        @NotNull
        @DecimalMin(value = "0.00")
        @Schema(example = "5000.00")
        BigDecimal dailyLimit,

        @NotBlank
        @Size(min = 3, max = 3)
        @Schema(description = "ISO 4217 currency code", example = "AED")
        String currency
) {
}
