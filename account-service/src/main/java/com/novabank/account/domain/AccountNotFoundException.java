package com.novabank.account.domain;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID customerId, UUID accountId) {
        super("Account not found for customer " + customerId + ": " + accountId);
    }

    public AccountNotFoundException(UUID accountId) {
        super("Account not found: " + accountId);
    }
}
