package com.novabank.customer.api;

import com.novabank.customer.api.dto.CreateCustomerRequest;
import com.novabank.customer.api.dto.CustomerResponse;
import com.novabank.customer.domain.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @Operation(summary = "Create a customer", description = "Creates a new customer with KYC status PENDING.")
    @ApiResponse(responseCode = "201", description = "Customer created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "409", description = "Email already in use")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse response = CustomerResponse.from(customerService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/customers/" + response.id())).body(response);
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Get a customer profile by id")
    @ApiResponse(responseCode = "200", description = "Customer found")
    @ApiResponse(responseCode = "404", description = "Customer not found")
    public CustomerResponse getById(@PathVariable UUID customerId) {
        return CustomerResponse.from(customerService.getById(customerId));
    }

    @GetMapping
    @Operation(summary = "Search customers", description = "Case-insensitive match on full name or email, paged.")
    @ApiResponse(responseCode = "200", description = "Matching customers")
    public Page<CustomerResponse> search(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return customerService.search(search, pageable).map(CustomerResponse::from);
    }
}
