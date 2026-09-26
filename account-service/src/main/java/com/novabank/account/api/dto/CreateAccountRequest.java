package com.novabank.account.api.dto;

import com.novabank.account.domain.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Request body to open a new account")
public record CreateAccountRequest(

        @NotNull
        AccountType type,

        @NotBlank
        @Size(min = 3, max = 3)
        @Schema(description = "ISO 4217 currency code", example = "AED")
        String currency,

        @DecimalMin(value = "0.00")
        @Schema(description = "Optional opening deposit. Defaults to 0.00.", example = "0.00")
        BigDecimal openingBalance
) {
    public CreateAccountRequest(AccountType type, String currency) {
        this(type, currency, null);
    }
}
