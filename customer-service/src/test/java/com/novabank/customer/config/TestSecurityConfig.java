package com.novabank.customer.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Replaces the production JwtDecoder (which fetches Entra ID's real signing keys) with one
 * that validates against TestJwtSupport's locally-generated key pair, so tests can mint their
 * own tokens without depending on a live Entra ID tenant.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public JwtDecoder testJwtDecoder() {
        return NimbusJwtDecoder.withPublicKey(TestJwtSupport.publicKey()).build();
    }
}
