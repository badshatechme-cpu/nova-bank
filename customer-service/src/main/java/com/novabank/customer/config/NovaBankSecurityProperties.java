package com.novabank.customer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "novabank.security")
public record NovaBankSecurityProperties(String expectedAudience, String customerIdClaim, String staffRole) {
}
