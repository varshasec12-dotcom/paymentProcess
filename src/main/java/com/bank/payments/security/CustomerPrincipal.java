package com.bank.payments.security;

import com.bank.payments.model.Customer;
import com.bank.payments.model.Customer.CustomerStatus;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

// The logged-in customer, kept in the HTTP session. Holds the id rather than the email
// so the session stays valid if the customer changes their email.
public class CustomerPrincipal implements UserDetails, CredentialsContainer {

    private final Long customerId;
    private final String email;
    private String passwordHash;
    private final boolean active;
    private final LocalDateTime lockedUntil;

    public CustomerPrincipal(Customer customer) {
        this.customerId = customer.getCustomerId();
        this.email = customer.getEmail();
        this.passwordHash = customer.getPasswordHash();
        this.active = customer.getStatus() == CustomerStatus.ACTIVE;
        this.lockedUntil = customer.getLockedUntil();
    }

    public Long getCustomerId() {
        return customerId;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    @Override
    public boolean isAccountNonLocked() {
        return lockedUntil == null || LocalDateTime.now().isAfter(lockedUntil);
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    // Called by Spring Security after login so the hash is not kept in the session
    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
