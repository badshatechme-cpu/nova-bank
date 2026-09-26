package com.novabank.card.api;

import com.novabank.card.domain.Card;
import com.novabank.card.domain.CardScheme;
import com.novabank.card.domain.CardStatus;
import com.novabank.card.domain.CardType;
import com.novabank.card.repository.CardRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CardControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    private int port;

    @Autowired
    private CardRepository cardRepository;

    private RestTestClient client;

    private UUID customerId;
    private UUID otherCustomerId;
    private UUID cardId;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        customerId = UUID.randomUUID();
        otherCustomerId = UUID.randomUUID();
        cardId = UUID.randomUUID();

        Card card = new Card(cardId, customerId, UUID.randomUUID(), CardType.DEBIT, CardScheme.VISA,
                "4111 **** **** 1234", "1234", 12, 2029, CardStatus.ACTIVE,
                new BigDecimal("5000.00"), "AED", null);
        cardRepository.save(card);
    }

    @Test
    void listsCardsForCustomer() {
        List<?> cards = client.get().uri("/api/v1/customers/{customerId}/cards", customerId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(List.class)
                .returnResult()
                .getResponseBody();

        assertThat(cards).hasSize(1);
    }

    @Test
    void getsOwnedCardWithMaskedPanOnly() {
        client.get().uri("/api/v1/customers/{customerId}/cards/{cardId}", customerId, cardId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body.get("maskedPan")).isEqualTo("4111 **** **** 1234");
                    assertThat(body.get("last4")).isEqualTo("1234");
                    assertThat(body).doesNotContainKey("pan");
                });
    }

    @Test
    void getCardForWrongCustomerReturns404() {
        client.get().uri("/api/v1/customers/{customerId}/cards/{cardId}", otherCustomerId, cardId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void blockThenUnblockTransitionsStatusCorrectly() {
        client.post().uri("/api/v1/customers/{customerId}/cards/{cardId}/block", customerId, cardId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reason", "Reported lost by customer"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body.get("status")).isEqualTo("BLOCKED");
                    assertThat(body.get("blockReason")).isEqualTo("Reported lost by customer");
                });

        client.post().uri("/api/v1/customers/{customerId}/cards/{cardId}/unblock", customerId, cardId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body.get("status")).isEqualTo("ACTIVE");
                    assertThat(body.get("blockReason")).isNull();
                });
    }

    @Test
    void blockingAnAlreadyBlockedCardReturns422() {
        client.post().uri("/api/v1/customers/{customerId}/cards/{cardId}/block", customerId, cardId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reason", "First block"))
                .exchange()
                .expectStatus().isOk();

        client.post().uri("/api/v1/customers/{customerId}/cards/{cardId}/block", customerId, cardId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reason", "Second block"))
                .exchange()
                .expectStatus().isEqualTo(422);
    }

    @Test
    void unblockingANonBlockedCardReturns422() {
        client.post().uri("/api/v1/customers/{customerId}/cards/{cardId}/unblock", customerId, cardId)
                .exchange()
                .expectStatus().isEqualTo(422);
    }

    @Test
    void blockWithoutReasonReturns400() {
        client.post().uri("/api/v1/customers/{customerId}/cards/{cardId}/block", customerId, cardId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reason", ""))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void issuesNewCardWithFabricatedMaskedPan() {
        UUID linkedAccountId = UUID.randomUUID();
        client.post().uri("/api/v1/customers/{customerId}/cards", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "linkedAccountId", linkedAccountId.toString(),
                        "type", "CREDIT",
                        "scheme", "MASTERCARD",
                        "dailyLimit", 3000.00,
                        "currency", "AED"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body.get("status")).isEqualTo("ACTIVE");
                    assertThat((String) body.get("maskedPan")).startsWith("5500");
                    assertThat((String) body.get("last4")).hasSize(4);
                });
    }
}
