package com.bank.payments.controller;

import com.bank.payments.dto.ContactChangeResponse;
import com.bank.payments.dto.CreateCustomerRequest;
import com.bank.payments.dto.StartContactChangeRequest;
import com.bank.payments.dto.VerifyOtpRequest;
import com.bank.payments.model.Customer;
import com.bank.payments.model.CustomerAuditLog;
import com.bank.payments.service.ContactChangeService;
import com.bank.payments.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Customer addCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        Customer customer = new Customer(request.firstName(), request.lastName(),
                request.email(), request.phoneNumber());
        return customerService.addCustomer(customer);
    }

    @GetMapping
    public List<Customer> getAllCustomers() {
        return customerService.getAllCustomers();
    }

    @GetMapping("/{customerId}")
    public Customer getCustomer(@PathVariable Long customerId) {
        return customerService.findCustomerById(customerId);
    }

    @DeleteMapping("/{customerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomer(@PathVariable Long customerId) {
        customerService.deleteCustomer(customerId);
    }


    @PostMapping("/{customerId}/contact-change")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ContactChangeResponse requestContactChange(@PathVariable Long customerId,
                                                      @Valid @RequestBody StartContactChangeRequest request) {
        return contactChangeService.requestChange(customerId, request.contactType(), request.newValue());
    }

    @PostMapping("/{customerId}/contact-change/{requestId}/verify")
    public Customer verifyContactChange(@PathVariable Long customerId, @PathVariable Long requestId,
                                        @Valid @RequestBody VerifyOtpRequest request) {
        return contactChangeService.verifyChange(customerId, requestId, request.otp());
    }

    @GetMapping("/{customerId}/audit-log")
    public List<CustomerAuditLog> getAuditLog(@PathVariable Long customerId) {
        return customerService.getAuditLog(customerId);
    }
}
