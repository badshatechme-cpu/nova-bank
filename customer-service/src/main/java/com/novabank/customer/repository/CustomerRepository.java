package com.novabank.customer.repository;

import com.novabank.customer.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Page<Customer> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String fullNamePart, String emailPart, Pageable pageable);
}
