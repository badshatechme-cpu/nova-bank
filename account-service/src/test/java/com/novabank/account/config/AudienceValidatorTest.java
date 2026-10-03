package com.novabank.account.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("api://expected-audience");

    @Test
    void acceptsTokenWithMatchingAudience() {
        Jwt jwt = jwtWithAudience(List.of("api://expected-audience"));
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenWithWrongAudience() {
        Jwt jwt = jwtWithAudience(List.of("api://some-other-api"));
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenWithNoAudience() {
        Jwt jwt = jwtWithAudience(List.of());
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    private Jwt jwtWithAudience(List<String> audience) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .audience(audience)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
