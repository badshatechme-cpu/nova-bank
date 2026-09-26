package com.novabank.account.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.novabank.account.api.dto.TransferRequest;
import com.novabank.account.api.dto.TransferResponse;
import com.novabank.account.repository.AccountRepository;
import com.novabank.account.repository.IdempotencyRecordRepository;
import com.novabank.account.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    /*
     * A private mapper rather than an injected bean: Spring Boot 4's Jackson autoconfiguration
     * builds its own tools.jackson JsonMapper for HTTP, and doesn't expose a classic
     * com.fasterxml ObjectMapper bean by default. This mapper is purely internal book-keeping
     * (idempotency response caching), not part of the HTTP contract, so a local instance is fine.
     */
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public TransferService(AccountRepository accountRepository, TransactionRepository transactionRepository,
                            IdempotencyRecordRepository idempotencyRecordRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Transactional
    public TransferResponse transfer(UUID customerId, String idempotencyKey, TransferRequest request) {
        String requestHash = hash(customerId + "|" + request.fromAccountId() + "|" + request.toAccountId()
                + "|" + request.amount() + "|" + request.currency() + "|" + request.narration());

        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findById(idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(idempotencyKey);
            }
            return readResponse(record.getResponseBody());
        }

        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new SelfTransferException();
        }

        Account fromAccount;
        Account toAccount;
        if (request.fromAccountId().compareTo(request.toAccountId()) < 0) {
            fromAccount = lock(request.fromAccountId());
            toAccount = lock(request.toAccountId());
        } else {
            toAccount = lock(request.toAccountId());
            fromAccount = lock(request.fromAccountId());
        }

        if (!fromAccount.getCustomerId().equals(customerId)) {
            throw new AccountNotFoundException(customerId, fromAccount.getId());
        }

        if (!request.currency().equals(fromAccount.getCurrency())
                || !request.currency().equals(toAccount.getCurrency())) {
            throw new CurrencyMismatchException(
                    "Transfer currency must match both accounts' currency (no FX conversion)");
        }

        fromAccount.debit(request.amount());
        toAccount.credit(request.amount());

        Instant bookedAt = request.bookedAt() != null ? request.bookedAt() : Instant.now();
        Transaction debitTransaction = new Transaction(UUID.randomUUID(), fromAccount.getId(), TransactionType.DEBIT,
                request.amount(), request.currency(), fromAccount.getBalance(),
                request.narration(), toAccount.getAccountNumber(), bookedAt);
        Transaction creditTransaction = new Transaction(UUID.randomUUID(), toAccount.getId(), TransactionType.CREDIT,
                request.amount(), request.currency(), toAccount.getBalance(),
                request.narration(), fromAccount.getAccountNumber(), bookedAt);

        transactionRepository.save(debitTransaction);
        transactionRepository.save(creditTransaction);
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        TransferResponse response = new TransferResponse(
                fromAccount.getId(), toAccount.getId(), request.amount(), request.currency(),
                request.narration(), debitTransaction.getId(), creditTransaction.getId(), bookedAt);

        idempotencyRecordRepository.save(new IdempotencyRecord(
                idempotencyKey, customerId, requestHash, writeResponse(response), Instant.now()));

        return response;
    }

    private Account lock(UUID accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String writeResponse(TransferResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize transfer response", e);
        }
    }

    private TransferResponse readResponse(String json) {
        try {
            return objectMapper.readValue(json, TransferResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize cached transfer response", e);
        }
    }
}
