package com.novabank.generator.client;

import com.novabank.generator.GeneratorProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Thin REST clients for the three services. Deliberately duplicates minimal request/response
 * shapes rather than sharing DTO classes with the services - each service's API is its own
 * contract, called over HTTP like any other consumer would.
 */
@Component
public class ServiceClients {

    private final RestClient customerServiceClient;
    private final RestClient accountServiceClient;
    private final RestClient cardServiceClient;

    public ServiceClients(GeneratorProperties properties) {
        this.customerServiceClient = RestClient.builder().baseUrl(properties.getBaseUrl().getCustomerService()).build();
        this.accountServiceClient = RestClient.builder().baseUrl(properties.getBaseUrl().getAccountService()).build();
        this.cardServiceClient = RestClient.builder().baseUrl(properties.getBaseUrl().getCardService()).build();
    }

    public UUID createCustomer(String fullName, String email, String mobile, LocalDate dateOfBirth, String nationality) {
        IdRef ref = customerServiceClient.post()
                .uri("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCustomerRequest(fullName, email, mobile, dateOfBirth, nationality))
                .retrieve()
                .body(IdRef.class);
        return ref.id();
    }

    public UUID createAccount(UUID customerId, String type, String currency) {
        return createAccount(customerId, type, currency, null);
    }

    public UUID createAccount(UUID customerId, String type, String currency, BigDecimal openingBalance) {
        IdRef ref = accountServiceClient.post()
                .uri("/api/v1/customers/{customerId}/accounts", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateAccountRequest(type, currency, openingBalance))
                .retrieve()
                .body(IdRef.class);
        return ref.id();
    }

    public void createCard(UUID customerId, UUID linkedAccountId, String type, String scheme,
                            BigDecimal dailyLimit, String currency) {
        cardServiceClient.post()
                .uri("/api/v1/customers/{customerId}/cards", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCardRequest(linkedAccountId, type, scheme, dailyLimit, currency))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Returns empty if the transfer was rejected as a business-rule violation (insufficient funds,
     * currency mismatch, self-transfer) - the generator skips these rather than failing the whole run.
     */
    public Optional<TransferRef> transfer(UUID customerId, UUID fromAccountId, UUID toAccountId,
                                           BigDecimal amount, String currency, String narration, Instant bookedAt) {
        try {
            TransferRef ref = accountServiceClient.post()
                    .uri("/api/v1/customers/{customerId}/transfers", customerId)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TransferRequest(fromAccountId, toAccountId, amount, currency, narration, bookedAt))
                    .retrieve()
                    .body(TransferRef.class);
            return Optional.of(ref);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 422) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public record IdRef(UUID id) {
    }

    public record TransferRef(UUID debitTransactionId, UUID creditTransactionId) {
    }

    record CreateCustomerRequest(String fullName, String email, String mobile,
                                  LocalDate dateOfBirth, String nationality) {
    }

    record CreateAccountRequest(String type, String currency, BigDecimal openingBalance) {
    }

    record CreateCardRequest(UUID linkedAccountId, String type, String scheme,
                              BigDecimal dailyLimit, String currency) {
    }

    record TransferRequest(UUID fromAccountId, UUID toAccountId, BigDecimal amount,
                            String currency, String narration, Instant bookedAt) {
    }
}
