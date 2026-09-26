package com.novabank.customer.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Request body to create a new customer")
public record CreateCustomerRequest(

        @NotBlank
        @Size(max = 200)
        @Schema(example = "Fatima Al Marri")
        String fullName,

        @NotBlank
        @Email
        @Size(max = 320)
        @Schema(example = "fatima.almarri@example.com")
        String email,

        @NotBlank
        @Size(max = 20)
        @Schema(example = "+971501234567")
        String mobile,

        @NotNull
        @Past
        @Schema(example = "1990-05-14")
        LocalDate dateOfBirth,

        @NotBlank
        @Size(max = 3)
        @Schema(description = "ISO 3166-1 alpha-3 country code", example = "ARE")
        String nationality
) {
}
