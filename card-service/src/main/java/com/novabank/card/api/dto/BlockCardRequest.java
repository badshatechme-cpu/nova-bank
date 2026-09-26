package com.novabank.card.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body to block a card")
public record BlockCardRequest(

        @NotBlank
        @Size(max = 200)
        @Schema(example = "Reported lost by customer")
        String reason
) {
}
