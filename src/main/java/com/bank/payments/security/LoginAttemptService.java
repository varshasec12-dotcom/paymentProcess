package com.bank.payments.security;

import com.bank.payments.model.Customer;
import com.bank.payments.model.Customer.CustomerStatus;
import com.bank.payments.repository.CustomerRepository;
import com.bank.payments.util.MaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

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
    public String loginFailed(String email, AuthenticationException ex, String clientIp) {
        Optional<Customer> customer = email == null ? Optional.empty()
                : customerRepository.findByEmailIgnoreCase(email.trim())
                        .filter(c -> c.getPasswordHash() != null && c.getStatus() == CustomerStatus.ACTIVE);

        if (ex instanceof LockedException && customer.isPresent() && customer.get().getLockedUntil() != null) {
            log.warn("Login rejected, account locked: customerId={}, ip={}", customer.get().getCustomerId(), clientIp);
            return lockedMessage(customer.get().getLockedUntil());
        }
        if (!(ex instanceof BadCredentialsException) || customer.isEmpty()) {
            log.warn("Login failed: email={}, reason={}, ip={}",
                    // Typed by the user: strip line breaks so it cannot forge extra log lines
                    email == null ? null : MaskingUtil.maskEmail(email.trim()).replaceAll("[\\r\\n]", "_"),
                    customer.isEmpty() ? "unknown or inactive customer" : ex.getClass().getSimpleName(), clientIp);
            return INVALID_CREDENTIALS;
        }

        Customer c = customer.get();
        int attempts = c.getFailedLoginAttempts() + 1;
        if (attempts >= maxAttempts) {
            c.setFailedLoginAttempts(0);
            c.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
            log.warn("Account locked after {} failed logins: customerId={}, lockedUntil={}, ip={}",
                    attempts, c.getCustomerId(), c.getLockedUntil(), clientIp);
            return lockedMessage(c.getLockedUntil());
        }
        c.setFailedLoginAttempts(attempts);
        log.warn("Login failed, wrong password: customerId={}, attempt={}/{}, ip={}",
                c.getCustomerId(), attempts, maxAttempts, clientIp);
        return INVALID_CREDENTIALS;
    }

    @Transactional
    public void loginSucceeded(Long customerId, String clientIp) {
        customerRepository.findById(customerId).ifPresent(c -> {
            c.setFailedLoginAttempts(0);
            c.setLockedUntil(null);
        });
        log.info("Login succeeded: customerId={}, ip={}", customerId, clientIp);
    }

    private String lockedMessage(LocalDateTime lockedUntil) {
        return "Too many failed attempts. Your account is locked until " + lockedUntil.format(TIME) + ".";
    }
}
