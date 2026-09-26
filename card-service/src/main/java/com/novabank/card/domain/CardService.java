package com.novabank.card.domain;

import com.novabank.card.api.dto.CreateCardRequest;
import com.novabank.card.repository.CardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CardService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CardRepository cardRepository;

    public CardService(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    @Transactional(readOnly = true)
    public List<Card> listForCustomer(UUID customerId) {
        return cardRepository.findByCustomerId(customerId);
    }

    public Card create(UUID customerId, CreateCardRequest request) {
        String last4 = String.format("%04d", RANDOM.nextInt(10000));
        String schemePrefix = request.scheme() == CardScheme.VISA ? "4111" : "5500";
        String maskedPan = schemePrefix + " **** **** " + last4;

        LocalDate expiry = LocalDate.now().plusYears(3 + RANDOM.nextInt(3));

        Card card = new Card(UUID.randomUUID(), customerId, request.linkedAccountId(), request.type(),
                request.scheme(), maskedPan, last4, expiry.getMonthValue(), expiry.getYear(),
                CardStatus.ACTIVE, request.dailyLimit(), request.currency(), null);
        return cardRepository.save(card);
    }

    @Transactional(readOnly = true)
    public Card getOwnedByCustomer(UUID customerId, UUID cardId) {
        return cardRepository.findByIdAndCustomerId(cardId, customerId)
                .orElseThrow(() -> new CardNotFoundException(customerId, cardId));
    }

    public Card block(UUID customerId, UUID cardId, String reason) {
        Card card = getOwnedByCustomer(customerId, cardId);
        card.block(reason);
        return cardRepository.save(card);
    }

    public Card unblock(UUID customerId, UUID cardId) {
        Card card = getOwnedByCustomer(customerId, cardId);
        card.unblock();
        return cardRepository.save(card);
    }
}
