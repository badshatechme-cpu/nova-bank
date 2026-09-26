package com.novabank.card.api.dto;

import com.novabank.card.domain.Card;
import com.novabank.card.domain.CardScheme;
import com.novabank.card.domain.CardStatus;
import com.novabank.card.domain.CardType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Card detail (masked - the full PAN is never stored or returned)")
public record CardResponse(
        UUID id,
        UUID customerId,
        UUID linkedAccountId,
        CardType type,
        CardScheme scheme,
        String maskedPan,
        String last4,
        int expiryMonth,
        int expiryYear,
        CardStatus status,
        BigDecimal dailyLimit,
        String currency,
        String blockReason
) {

    public static CardResponse from(Card card) {
        return new CardResponse(
                card.getId(),
                card.getCustomerId(),
                card.getLinkedAccountId(),
                card.getType(),
                card.getScheme(),
                card.getMaskedPan(),
                card.getLast4(),
                card.getExpiryMonth(),
                card.getExpiryYear(),
                card.getStatus(),
                card.getDailyLimit(),
                card.getCurrency(),
                card.getBlockReason());
    }
}
