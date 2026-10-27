package com.bank.payments.service;

import com.bank.payments.dto.RegisterRequest;
import com.bank.payments.model.Customer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final CustomerService customerService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(CustomerService customerService, PasswordEncoder passwordEncoder) {
        this.customerService = customerService;
        this.passwordEncoder = passwordEncoder;
    }

    public Customer register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        Customer customer = new Customer(request.firstName().trim(), request.lastName().trim(),
                request.email(), request.phoneNumber());
        customer.setPasswordHash(passwordEncoder.encode(request.password()));
        // addCustomer rejects an email or phone number that is already registered
        return customerService.addCustomer(customer);
    }
}
