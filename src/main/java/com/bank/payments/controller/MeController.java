package com.bank.payments.controller;

import com.bank.payments.dto.AccountResponse;
import com.bank.payments.model.Customer;
import com.bank.payments.security.CustomerPrincipal;
import com.bank.payments.service.AccountService;
import com.bank.payments.service.CustomerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Data for the logged-in customer only; the customer id comes from the session, never the URL
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final CustomerService customerService;
    private final AccountService accountService;

    public MeController(CustomerService customerService, AccountService accountService) {
        this.customerService = customerService;
        this.accountService = accountService;
    }

    @GetMapping
    public Customer getProfile(@AuthenticationPrincipal CustomerPrincipal principal) {
        return customerService.findCustomerById(principal.getCustomerId());
    }

    @GetMapping("/accounts")
    public List<AccountResponse> getAccounts(@AuthenticationPrincipal CustomerPrincipal principal) {
        return accountService.getAccountsByCustomer(principal.getCustomerId()).stream()
                .map(AccountResponse::from)
                .toList();
    }
}
