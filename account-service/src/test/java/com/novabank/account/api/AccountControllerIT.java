package com.novabank.account.api;

import com.novabank.account.domain.Account;
import com.novabank.account.domain.AccountStatus;
import com.novabank.account.domain.AccountType;
import com.novabank.account.domain.Transaction;
import com.novabank.account.domain.TransactionType;
import com.novabank.account.repository.AccountRepository;
import com.novabank.account.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    private int port;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private RestTestClient client;

    private UUID customerId;
    private UUID otherCustomerId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        customerId = UUID.randomUUID();
        otherCustomerId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        String suffix = accountId.toString().substring(0, 8);
        Account account = new Account(accountId, customerId, "AC-" + suffix, "AE07" + suffix + "0123456789",
                AccountType.CURRENT, "AED", new BigDecimal("1500.00"), AccountStatus.ACTIVE, Instant.now());
        accountRepository.save(account);

        Instant now = Instant.now();
        transactionRepository.save(new Transaction(UUID.randomUUID(), accountId, TransactionType.CREDIT,
                new BigDecimal("2000.00"), "AED", new BigDecimal("2000.00"), "Salary", "Employer LLC",
                now.minus(10, ChronoUnit.DAYS)));
        transactionRepository.save(new Transaction(UUID.randomUUID(), accountId, TransactionType.DEBIT,
                new BigDecimal("500.00"), "AED", new BigDecimal("1500.00"), "Groceries", "Carrefour",
                now.minus(1, ChronoUnit.DAYS)));
    }

    @Test
    void listsAccountsForCustomer() {
        List<?> accounts = client.get().uri("/api/v1/customers/{customerId}/accounts", customerId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(List.class)
                .returnResult()
                .getResponseBody();

        assertThat(accounts).hasSize(1);
    }

    @Test
    void getsOwnedAccount() {
        client.get().uri("/api/v1/customers/{customerId}/accounts/{accountId}", customerId, accountId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("balance")).isEqualTo(1500.00));
    }

    @Test
    void getAccountForWrongCustomerReturns404() {
        client.get().uri("/api/v1/customers/{customerId}/accounts/{accountId}", otherCustomerId, accountId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void listsTransactionsWithDateFilter() {
        Map<?, ?> page = client.get()
                .uri("/api/v1/customers/{customerId}/accounts/{accountId}/transactions?from={from}",
                        customerId, accountId, Instant.now().minus(3, ChronoUnit.DAYS).toString().substring(0, 10))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        List<?> content = (List<?>) page.get("content");
        assertThat(content).hasSize(1);
    }

    @Test
    void listTransactionsForWrongCustomerReturns404() {
        client.get().uri("/api/v1/customers/{customerId}/accounts/{accountId}/transactions",
                        otherCustomerId, accountId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void opensNewAccountWithZeroBalance() {
        client.post().uri("/api/v1/customers/{customerId}/accounts", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "SAVINGS", "currency", "AED"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body.get("type")).isEqualTo("SAVINGS");
                    assertThat(body.get("balance")).isEqualTo(0.00);
                    assertThat(body.get("status")).isEqualTo("ACTIVE");
                });
    }

    @Test
    void opensNewAccountWithAnOpeningBalance() {
        client.post().uri("/api/v1/customers/{customerId}/accounts", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "CURRENT", "currency", "AED", "openingBalance", 1000000.00))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("balance")).isEqualTo(1000000.00));
    }
}
