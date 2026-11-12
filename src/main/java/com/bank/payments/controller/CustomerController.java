package com.bank.payments.controller;

import com.bank.payments.dto.ContactChangeResponse;
import com.bank.payments.dto.CreateCustomerRequest;
import com.bank.payments.dto.StartContactChangeRequest;
import com.bank.payments.dto.VerifyOtpRequest;
import com.bank.payments.model.Customer;
import com.bank.payments.model.CustomerAuditLog;
import com.bank.payments.security.CustomerPrincipal;
import com.bank.payments.service.ContactChangeService;
import com.bank.payments.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final ContactChangeService contactChangeService;

    public CustomerController(CustomerService customerService, ContactChangeService contactChangeService) {
        this.customerService = customerService;
        this.contactChangeService = contactChangeService;
    }

    // ADMIN only (see SecurityConfig)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Customer addCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        Customer customer = new Customer(request.firstName(), request.lastName(),
                request.email(), request.phoneNumber());
        return customerService.addCustomer(customer);
    }

    // ADMIN only (see SecurityConfig)
    @GetMapping
    public List<Customer> getAllCustomers() {
        return customerService.getAllCustomers();
    }

    @GetMapping("/{customerId}")
    public Customer getCustomer(@AuthenticationPrincipal CustomerPrincipal principal,
                                @PathVariable Long customerId) {
        requireAccess(principal, customerId);
        return customerService.findCustomerById(customerId);
    }

    // ADMIN only (see SecurityConfig)
    @DeleteMapping("/{customerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomer(@PathVariable Long customerId) {
        customerService.deleteCustomer(customerId);
    }

    // Only the customer themselves can change their email or phone, not even bank staff
    @PostMapping("/{customerId}/contact-change")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ContactChangeResponse requestContactChange(@AuthenticationPrincipal CustomerPrincipal principal,
                                                      @PathVariable Long customerId,
                                                      @Valid @RequestBody StartContactChangeRequest request) {
        requireSelf(principal, customerId);
        return contactChangeService.requestChange(customerId, request.contactType(), request.newValue());
    }

    @PostMapping("/{customerId}/contact-change/{requestId}/verify")
    public Customer verifyContactChange(@AuthenticationPrincipal CustomerPrincipal principal,
                                        @PathVariable Long customerId, @PathVariable Long requestId,
                                        @Valid @RequestBody VerifyOtpRequest request) {
        requireSelf(principal, customerId);
        return contactChangeService.verifyChange(customerId, requestId, request.otp());
    }

    @GetMapping("/{customerId}/audit-log")
    public List<CustomerAuditLog> getAuditLog(@AuthenticationPrincipal CustomerPrincipal principal,
                                              @PathVariable Long customerId) {
        requireAccess(principal, customerId);
        return customerService.getAuditLog(customerId);
    }

    // AccessDeniedException is turned into the 403 JSON response by SecurityConfig
    private void requireAccess(CustomerPrincipal principal, Long customerId) {
        if (!principal.canAccessCustomer(customerId)) {
            throw new AccessDeniedException("Access denied.");
        }
    }

    private void requireSelf(CustomerPrincipal principal, Long customerId) {
        if (!principal.getCustomerId().equals(customerId)) {
            throw new AccessDeniedException("Access denied.");
        }
    }
}
