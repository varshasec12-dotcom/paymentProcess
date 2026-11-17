package com.bank.payments.service;

import com.bank.payments.dto.RegisterRequest;
import com.bank.payments.model.Customer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final CustomerService customerService;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(CustomerService customerService, AccountService accountService,
                       PasswordEncoder passwordEncoder) {
        this.customerService = customerService;
        this.accountService = accountService;
        this.passwordEncoder = passwordEncoder;
    }

    // Creates the customer and their first account together: if the account cannot be opened,
    // the customer is not saved either
    @Transactional
    public Customer register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        Customer customer = new Customer(request.firstName().trim(), request.lastName().trim(),
                request.email(), request.phoneNumber());
        customer.setPasswordHash(passwordEncoder.encode(request.password()));
        // addCustomer rejects an email or phone number that is already registered
        Customer saved = customerService.addCustomer(customer);
        accountService.openAccount(saved.getCustomerId(), request.accountType());
        return saved;
    }
}
