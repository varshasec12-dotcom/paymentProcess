package com.bank.payments.security;

import com.bank.payments.repository.CustomerRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomerUserDetailsService implements UserDetailsService {

    private final CustomerRepository customerRepository;

    public CustomerUserDetailsService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    // Login is by email. Customers without a password have not registered for online banking.
    @Override
    public UserDetails loadUserByUsername(String email) {
        return customerRepository.findByEmailIgnoreCase(email.trim())
                .filter(customer -> customer.getPasswordHash() != null)
                .map(CustomerPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
    }
}
