package com.novabank.card.domain;

import java.util.UUID;

public class CardNotBlockedException extends RuntimeException {

    public CardNotBlockedException(UUID cardId) {
        super("Card is not blocked: " + cardId);
    }
}
