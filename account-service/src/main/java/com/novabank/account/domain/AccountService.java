package com.novabank.account.domain;

import com.novabank.account.api.dto.CreateAccountRequest;
import com.novabank.account.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public List<Account> listForCustomer(UUID customerId) {
        return accountRepository.findByCustomerId(customerId);
    }

    public Account getOwnedByCustomer(UUID customerId, UUID accountId) {
        return accountRepository.findByIdAndCustomerId(accountId, customerId)
                .orElseThrow(() -> new AccountNotFoundException(customerId, accountId));
    }

    @Transactional
    public Account create(UUID customerId, CreateAccountRequest request) {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().replace("-", "").substring(0, 10).toUpperCase();
        BigDecimal openingBalance = request.openingBalance() != null ? request.openingBalance() : new BigDecimal("0.00");
        Account account = new Account(
                id, customerId, "AC-" + suffix, "AE07" + suffix + "0000000",
                request.type(), request.currency(), openingBalance, AccountStatus.ACTIVE, Instant.now());
        return accountRepository.save(account);
    }
}
