package com.novabank.account.domain;

import com.novabank.account.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;

    public TransactionService(TransactionRepository transactionRepository, AccountService accountService) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
    }

    public Page<Transaction> listForAccount(UUID customerId, UUID accountId,
                                             LocalDate from, LocalDate to, Pageable pageable) {
        accountService.getOwnedByCustomer(customerId, accountId);

        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.EPOCH;
        Instant toInstant = to != null
                ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                : Instant.now();

        return transactionRepository.findByAccountIdAndBookedAtBetween(accountId, fromInstant, toInstant, pageable);
    }
}
