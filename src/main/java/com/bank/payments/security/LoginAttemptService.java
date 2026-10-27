package com.bank.payments.security;

import com.bank.payments.model.Customer;
import com.bank.payments.model.Customer.CustomerStatus;
import com.bank.payments.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class LoginAttemptService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password.";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final CustomerRepository customerRepository;
    private final int maxAttempts;
    private final long lockMinutes;

    public LoginAttemptService(CustomerRepository customerRepository,
                               @Value("${payments.auth.max-login-attempts}") int maxAttempts,
                               @Value("${payments.auth.lock-minutes}") long lockMinutes) {
        this.customerRepository = customerRepository;
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
    }

    // Returns the message to show the user. Unknown email, wrong password and inactive
    // customer all get the same message so the login form cannot be used to find customers.
    @Transactional
    public String loginFailed(String email, AuthenticationException ex) {
        Optional<Customer> customer = email == null ? Optional.empty()
                : customerRepository.findByEmailIgnoreCase(email.trim())
                        .filter(c -> c.getPasswordHash() != null && c.getStatus() == CustomerStatus.ACTIVE);

        if (ex instanceof LockedException && customer.isPresent() && customer.get().getLockedUntil() != null) {
            return lockedMessage(customer.get().getLockedUntil());
        }
        if (!(ex instanceof BadCredentialsException) || customer.isEmpty()) {
            return INVALID_CREDENTIALS;
        }

        Customer c = customer.get();
        int attempts = c.getFailedLoginAttempts() + 1;
        if (attempts >= maxAttempts) {
            c.setFailedLoginAttempts(0);
            c.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
            return lockedMessage(c.getLockedUntil());
        }
        c.setFailedLoginAttempts(attempts);
        return INVALID_CREDENTIALS;
    }

    @Transactional
    public void loginSucceeded(Long customerId) {
        customerRepository.findById(customerId).ifPresent(c -> {
            c.setFailedLoginAttempts(0);
            c.setLockedUntil(null);
        });
    }

    private String lockedMessage(LocalDateTime lockedUntil) {
        return "Too many failed attempts. Your account is locked until " + lockedUntil.format(TIME) + ".";
    }
}
