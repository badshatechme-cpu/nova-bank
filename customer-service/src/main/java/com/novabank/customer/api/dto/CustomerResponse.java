package com.novabank.customer.api.dto;

import com.novabank.customer.domain.Customer;
import com.novabank.customer.domain.KycStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Customer profile")
public record CustomerResponse(
        UUID id,
        String fullName,
        String email,
        String mobile,
        LocalDate dateOfBirth,
        String nationality,
        KycStatus kycStatus,
        Instant createdAt
) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getMobile(),
                customer.getDateOfBirth(),
                customer.getNationality(),
                customer.getKycStatus(),
                customer.getCreatedAt());
    }
}
