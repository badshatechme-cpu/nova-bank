package com.novabank.customer.api;

import com.novabank.customer.api.dto.CreateCustomerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void createThenGetThenSearch() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "Fatima Al Marri", "fatima.almarri@example.com", "+971501234567",
                LocalDate.of(1990, 5, 14), "ARE");

        Map<?, ?> created = client.post().uri("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        String id = (String) created.get("id");
        assertThat(id).isNotBlank();
        assertThat(created.get("kycStatus")).isEqualTo("PENDING");

        client.get().uri("/api/v1/customers/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> assertThat(body.get("email")).isEqualTo("fatima.almarri@example.com"));

        client.get().uri("/api/v1/customers?search=fatima")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> assertThat((Iterable<?>) body.get("content")).isNotEmpty());
    }

    @Test
    void createWithInvalidRequestReturns400() {
        CreateCustomerRequest invalid = new CreateCustomerRequest(
                "", "not-an-email", "", LocalDate.now().plusDays(1), "ARE");

        client.post().uri("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(invalid)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getUnknownCustomerReturns404() {
        client.get().uri("/api/v1/customers/{id}", UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound();
    }
}
