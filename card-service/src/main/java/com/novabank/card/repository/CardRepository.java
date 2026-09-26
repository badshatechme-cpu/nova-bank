package com.novabank.card.repository;

import com.novabank.card.domain.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {

    List<Card> findByCustomerId(UUID customerId);

    Optional<Card> findByIdAndCustomerId(UUID id, UUID customerId);
}
