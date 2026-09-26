package com.novabank.customer.domain;

import com.novabank.customer.api.dto.CreateCustomerRequest;
import com.novabank.customer.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer create(CreateCustomerRequest request) {
        Customer customer = new Customer(
                UUID.randomUUID(),
                request.fullName(),
                request.email(),
                request.mobile(),
                request.dateOfBirth(),
                request.nationality(),
                KycStatus.PENDING,
                Instant.now());
        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public Customer getById(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    @Transactional(readOnly = true)
    public Page<Customer> search(String search, Pageable pageable) {
        String term = search == null ? "" : search;
        return customerRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                term, term, pageable);
    }
}
