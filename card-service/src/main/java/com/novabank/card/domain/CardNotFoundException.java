package com.novabank.card.domain;

import java.util.UUID;

public class CardNotFoundException extends RuntimeException {

    public CardNotFoundException(UUID customerId, UUID cardId) {
        super("Card not found for customer " + customerId + ": " + cardId);
    }
}
