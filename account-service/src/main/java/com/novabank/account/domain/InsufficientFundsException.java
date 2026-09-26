package com.novabank.account.domain;

import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID accountId) {
        super("Insufficient funds in account: " + accountId);
    }
}
