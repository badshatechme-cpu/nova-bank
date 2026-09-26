package com.novabank.account.domain;

public class SelfTransferException extends RuntimeException {

    public SelfTransferException() {
        super("Cannot transfer an account to itself");
    }
}
