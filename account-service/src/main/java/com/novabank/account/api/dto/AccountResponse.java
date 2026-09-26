package com.novabank.account.api.dto;

import com.novabank.account.domain.Account;
import com.novabank.account.domain.AccountStatus;
import com.novabank.account.domain.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Account with current balance")
public record AccountResponse(
        UUID id,
        UUID customerId,
        String accountNumber,
        String iban,
        AccountType type,
        String currency,
        BigDecimal balance,
        AccountStatus status,
        Instant openedAt
) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getCustomerId(),
                account.getAccountNumber(),
                account.getIban(),
                account.getType(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus(),
                account.getOpenedAt());
    }
}
