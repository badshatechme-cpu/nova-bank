package com.novabank.account.api;

import com.novabank.account.api.dto.TransferRequest;
import com.novabank.account.domain.Account;
import com.novabank.account.domain.AccountStatus;
import com.novabank.account.domain.AccountType;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransferControllerIT {

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
    private UUID fromAccountId;
    private UUID toAccountId;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        customerId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();
        fromAccountId = UUID.randomUUID();
        toAccountId = UUID.randomUUID();

        accountRepository.save(newAccount(fromAccountId, customerId, new BigDecimal("1000.00")));
        // toAccount belongs to a DIFFERENT customer, proving transfers aren't restricted to "my own accounts".
        accountRepository.save(newAccount(toAccountId, otherCustomerId, new BigDecimal("500.00")));
    }

    private Account newAccount(UUID id, UUID ownerCustomerId, BigDecimal balance) {
        String suffix = id.toString().substring(0, 8);
        return new Account(id, ownerCustomerId, "AC-" + suffix, "AE07" + suffix + "0123456789",
                AccountType.CURRENT, "AED", balance, AccountStatus.ACTIVE, Instant.now());
    }

    private TransferRequest requestOf(UUID from, UUID to, String amount, String narration) {
        return new TransferRequest(from, to, new BigDecimal(amount), "AED", narration);
    }

    @Test
    void transferSucceedsAndPostsBothSides() {
        Map<?, ?> body = client.post().uri("/api/v1/customers/{customerId}/transfers", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "success-key-1")
                .body(requestOf(fromAccountId, toAccountId, "100.00", "Rent"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.get("debitTransactionId")).isNotNull();
        assertThat(body.get("creditTransactionId")).isNotNull();

        assertThat(accountRepository.findById(fromAccountId).orElseThrow().getBalance())
                .isEqualByComparingTo("900.00");
        assertThat(accountRepository.findById(toAccountId).orElseThrow().getBalance())
                .isEqualByComparingTo("600.00");
    }

    @Test
    void insufficientFundsReturns422AndChangesNothing() {
        client.post().uri("/api/v1/customers/{customerId}/transfers", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "insufficient-key-1")
                .body(requestOf(fromAccountId, toAccountId, "999999.00", "Too much"))
                .exchange()
                .expectStatus().isEqualTo(422);

        assertThat(accountRepository.findById(fromAccountId).orElseThrow().getBalance())
                .isEqualByComparingTo("1000.00");
        assertThat(accountRepository.findById(toAccountId).orElseThrow().getBalance())
                .isEqualByComparingTo("500.00");
    }

    @Test
    void replayingTheSameIdempotencyKeyReturnsOriginalResultWithoutDoublePosting() {
        TransferRequest request = requestOf(fromAccountId, toAccountId, "50.00", "Replay test");

        Map<?, ?> first = client.post().uri("/api/v1/customers/{customerId}/transfers", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "replay-key-1")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        Map<?, ?> second = client.post().uri("/api/v1/customers/{customerId}/transfers", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "replay-key-1")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(second.get("debitTransactionId")).isEqualTo(first.get("debitTransactionId"));
        assertThat(second.get("creditTransactionId")).isEqualTo(first.get("creditTransactionId"));

        assertThat(transactionRepository.findByAccountIdAndBookedAtBetween(
                fromAccountId, Instant.EPOCH, Instant.now().plusSeconds(60),
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements()).isEqualTo(1);

        assertThat(accountRepository.findById(fromAccountId).orElseThrow().getBalance())
                .isEqualByComparingTo("950.00");
    }

    @Test
    void concurrentTransfersDrainingTheSameAccountNeverGoNegative() throws Exception {
        UUID drainAccountId = UUID.randomUUID();
        UUID sinkAccountId = UUID.randomUUID();
        accountRepository.save(newAccount(drainAccountId, customerId, new BigDecimal("100.00")));
        accountRepository.save(newAccount(sinkAccountId, UUID.randomUUID(), new BigDecimal("0.00")));

        Callable<Integer> transferOf80 = () -> client.post()
                .uri("/api/v1/customers/{customerId}/transfers", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "concurrent-" + UUID.randomUUID())
                .body(requestOf(drainAccountId, sinkAccountId, "80.00", "Concurrent drain"))
                .exchange()
                .returnResult(Void.class)
                .getStatus()
                .value();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = executor.invokeAll(List.of(transferOf80, transferOf80));
            List<Integer> statusCodes = List.of(futures.get(0).get(), futures.get(1).get());

            assertThat(statusCodes).containsExactlyInAnyOrder(201, 422);
        } finally {
            executor.shutdown();
        }

        BigDecimal finalBalance = accountRepository.findById(drainAccountId).orElseThrow().getBalance();
        assertThat(finalBalance).isEqualByComparingTo("20.00");
        assertThat(finalBalance.signum()).isGreaterThanOrEqualTo(0);
    }
}
