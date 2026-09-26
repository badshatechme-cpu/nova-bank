package com.novabank.card.domain;

import java.util.UUID;

public class CardAlreadyBlockedException extends RuntimeException {

    public CardAlreadyBlockedException(UUID cardId) {
        super("Card is already blocked: " + cardId);
    }
}
