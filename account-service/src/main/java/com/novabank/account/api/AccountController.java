package com.novabank.account.api;

import com.novabank.account.api.dto.AccountResponse;
import com.novabank.account.api.dto.CreateAccountRequest;
import com.novabank.account.api.dto.TransactionResponse;
import com.novabank.account.domain.AccountService;
import com.novabank.account.domain.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/accounts")
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;

    public AccountController(AccountService accountService, TransactionService transactionService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    @GetMapping
    @Operation(summary = "List a customer's accounts")
    @ApiResponse(responseCode = "200", description = "Accounts for the customer")
    public List<AccountResponse> listAccounts(@PathVariable UUID customerId) {
        return accountService.listForCustomer(customerId).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @PostMapping
    @Operation(summary = "Open a new account", description = "Balance starts at the optional openingBalance, or 0.00.")
    @ApiResponse(responseCode = "201", description = "Account opened")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<AccountResponse> openAccount(
            @PathVariable UUID customerId, @Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = AccountResponse.from(accountService.create(customerId, request));
        return ResponseEntity.created(URI.create(
                "/api/v1/customers/" + customerId + "/accounts/" + response.id())).body(response);
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get an account and its balance")
    @ApiResponse(responseCode = "200", description = "Account found")
    @ApiResponse(responseCode = "404", description = "Account not found for this customer")
    public AccountResponse getAccount(@PathVariable UUID customerId, @PathVariable UUID accountId) {
        return AccountResponse.from(accountService.getOwnedByCustomer(customerId, accountId));
    }

    @GetMapping("/{accountId}/transactions")
    @Operation(summary = "List transactions for an account", description = "Paged, optionally filtered by booking date.")
    @ApiResponse(responseCode = "200", description = "Matching transactions")
    @ApiResponse(responseCode = "404", description = "Account not found for this customer")
    public Page<TransactionResponse> listTransactions(
            @PathVariable UUID customerId,
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        return transactionService.listForAccount(customerId, accountId, from, to, pageable)
                .map(TransactionResponse::from);
    }
}
