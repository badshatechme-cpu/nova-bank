package com.novabank.account.repository;

import com.novabank.account.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByAccountIdAndBookedAtBetween(
            UUID accountId, Instant from, Instant to, Pageable pageable);
}
