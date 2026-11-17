package com.bank.payments.service;

import com.bank.payments.exception.CustomerNotFoundException;
import com.bank.payments.exception.DuplicateContactException;
import com.bank.payments.model.Customer;
import com.bank.payments.model.Customer.CustomerStatus;
import com.bank.payments.model.CustomerAuditLog;
import com.bank.payments.model.CustomerAuditLog.AuditAction;
import com.bank.payments.repository.CustomerAuditLogRepository;
import com.bank.payments.repository.CustomerRepository;
import com.bank.payments.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final CustomerAuditLogRepository auditLogRepository;
    private final List<String> allowedEmailDomains;

    public CustomerService(CustomerRepository customerRepository,
                           CustomerAuditLogRepository auditLogRepository,
                           @Value("${payments.customer.allowed-email-domains}") List<String> allowedEmailDomains) {
        this.customerRepository = customerRepository;
        this.auditLogRepository = auditLogRepository;
        this.allowedEmailDomains = allowedEmailDomains.stream()
                .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isEmpty())
                .toList();
    }

    // Checks a new email address and returns it in its stored form. Used for registration, bank staff
    // creating a customer and a customer changing their email, so all three follow the same rules.
    public String validateEmail(String email) {
        if (!ValidationUtil.isValidEmail(email)) {
            throw new IllegalArgumentException("Invalid email address: " + email);
        }
        String trimmed = email.trim();
        String domain = trimmed.substring(trimmed.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        if (!allowedEmailDomains.contains(domain)) {
            throw new IllegalArgumentException("Email domain not accepted. Please use an address ending in "
                    + allowedEmailDomainsText() + ".");
        }
        return trimmed;
    }

    private String allowedEmailDomainsText() {
        return allowedEmailDomains.stream().map(domain -> "@" + domain).collect(Collectors.joining(", "));
    }

    // Used by self-registration and by bank staff creating a customer
    public Customer addCustomer(Customer customer) {
        customer.setEmail(validateEmail(customer.getEmail()));
        if (customerRepository.existsByEmailIgnoreCase(customer.getEmail())) {
            throw new DuplicateContactException("Email address is already registered to another customer.");
        }
        if (customerRepository.existsByPhoneNumber(customer.getPhoneNumber())) {
            throw new DuplicateContactException("Phone number is already registered to another customer.");
        }
        Customer saved = customerRepository.save(customer);
        log.info("Customer created: customerId={}", saved.getCustomerId());
        return saved;
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findByStatus(CustomerStatus.ACTIVE);
    }

    public Customer findCustomerById(Long customerId) {
        return customerRepository.findById(customerId)
                .filter(customer -> customer.getStatus() == CustomerStatus.ACTIVE)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + customerId));
    }

    // Soft delete: the row is kept (accounts and transactions still reference it) and only marked INACTIVE
    @Transactional
    public void deleteCustomer(Long customerId) {
        Customer customer = findCustomerById(customerId);
        customer.setStatus(CustomerStatus.INACTIVE);
        customerRepository.save(customer);
        auditLogRepository.save(new CustomerAuditLog(customer, AuditAction.CUSTOMER_DEACTIVATED,
                CustomerStatus.ACTIVE.name(), CustomerStatus.INACTIVE.name(), LocalDateTime.now()));
        log.info("Customer deactivated: customerId={}", customerId);
    }

    // Includes inactive customers, so a deactivation stays visible in the history
    public List<CustomerAuditLog> getAuditLog(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new CustomerNotFoundException("Customer not found: " + customerId);
        }
        return auditLogRepository.findByCustomer_CustomerIdOrderByChangedAtDesc(customerId);
    }
}
