package com.bank.payments.controller;

import com.bank.payments.dto.AmountRequest;
import com.bank.payments.dto.CreateAccountRequest;
import com.bank.payments.dto.TransferRequest;
import com.bank.payments.model.Account;
import com.bank.payments.security.CustomerPrincipal;
import com.bank.payments.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Customers can only use their own accounts. Bank staff (ADMIN) can view any account but,
// like everyone else, can only move money out of their own.
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Account openAccount(@AuthenticationPrincipal CustomerPrincipal principal,
                               @Valid @RequestBody CreateAccountRequest request) {
        return accountService.openAccount(principal.getCustomerId(), request.accountType());
    }

    // ADMIN only (see SecurityConfig)
    @GetMapping
    public List<Account> getAllAccounts() {
        return accountService.getAllAccounts();
    }

    @GetMapping("/{accountNumber}")
    public Account getAccount(@AuthenticationPrincipal CustomerPrincipal principal,
                              @PathVariable String accountNumber) {
        return principal.isAdmin()
                ? accountService.findAccountByNumber(accountNumber)
                : accountService.findOwnedAccount(accountNumber, principal.getCustomerId());
    }

    @PostMapping("/{accountNumber}/deposit")
    public Account deposit(@AuthenticationPrincipal CustomerPrincipal principal,
                           @PathVariable String accountNumber, @Valid @RequestBody AmountRequest request) {
        return accountService.deposit(accountNumber, request.amount(), principal.getCustomerId());
    }

    @PostMapping("/{accountNumber}/withdraw")
    public Account withdraw(@AuthenticationPrincipal CustomerPrincipal principal,
                            @PathVariable String accountNumber, @Valid @RequestBody AmountRequest request) {
        return accountService.withdraw(accountNumber, request.amount(), principal.getCustomerId());
    }

    @PostMapping("/transfer")
    public void transfer(@AuthenticationPrincipal CustomerPrincipal principal,
                         @Valid @RequestBody TransferRequest request) {
        accountService.transfer(request.fromAccountNumber(), request.toAccountNumber(), request.amount(),
                principal.getCustomerId());
    }
}
