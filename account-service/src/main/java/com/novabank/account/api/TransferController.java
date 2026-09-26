package com.novabank.account.api;

import com.novabank.account.api.dto.TransferRequest;
import com.novabank.account.api.dto.TransferResponse;
import com.novabank.account.domain.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Transfer funds between two accounts",
            description = "Debits fromAccountId and credits toAccountId in one database transaction. "
                    + "Requires an Idempotency-Key header; replaying the same key returns the original result.")
    @ApiResponse(responseCode = "201", description = "Transfer completed")
    @ApiResponse(responseCode = "400", description = "Validation failed or missing Idempotency-Key header")
    @ApiResponse(responseCode = "404", description = "An account was not found, or fromAccountId is not owned by this customer")
    @ApiResponse(responseCode = "409", description = "Idempotency-Key reused with a different request")
    @ApiResponse(responseCode = "422", description = "Insufficient funds, currency mismatch, or self-transfer")
    public TransferResponse transfer(
            @PathVariable UUID customerId,
            @Parameter(description = "Client-generated key; replaying it returns the original result instead of transferring again", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return transferService.transfer(customerId, idempotencyKey, request);
    }
}
