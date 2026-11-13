package com.bank.payments.controller;

import com.bank.payments.dto.AccountResponse;
import com.bank.payments.dto.AmountRequest;
import com.bank.payments.dto.CreateAccountRequest;
import com.bank.payments.dto.TransferRequest;
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
    public AccountResponse openAccount(@AuthenticationPrincipal CustomerPrincipal principal,
                                       @Valid @RequestBody CreateAccountRequest request) {
        return AccountResponse.from(accountService.openAccount(principal.getCustomerId(), request.accountType()));
    }

    // ADMIN only (see SecurityConfig)
    @GetMapping
    public List<AccountResponse> getAllAccounts() {
        return accountService.getAllAccounts().stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{accountNumber}")
    public AccountResponse getAccount(@AuthenticationPrincipal CustomerPrincipal principal,
                                      @PathVariable String accountNumber) {
        return AccountResponse.from(principal.isAdmin()
                ? accountService.findAccountByNumber(accountNumber)
                : accountService.findOwnedAccount(accountNumber, principal.getCustomerId()));
    }

    @PostMapping("/{accountNumber}/deposit")
    public AccountResponse deposit(@AuthenticationPrincipal CustomerPrincipal principal,
                                   @PathVariable String accountNumber, @Valid @RequestBody AmountRequest request) {
        return AccountResponse.from(
                accountService.deposit(accountNumber, request.amount(), principal.getCustomerId()));
    }

    @PostMapping("/{accountNumber}/withdraw")
    public AccountResponse withdraw(@AuthenticationPrincipal CustomerPrincipal principal,
                                    @PathVariable String accountNumber, @Valid @RequestBody AmountRequest request) {
        return AccountResponse.from(
                accountService.withdraw(accountNumber, request.amount(), principal.getCustomerId()));
    }

    @PostMapping("/transfer")
    public void transfer(@AuthenticationPrincipal CustomerPrincipal principal,
                         @Valid @RequestBody TransferRequest request) {
        accountService.transfer(request.fromAccountNumber(), request.toAccountNumber(), request.amount(),
                principal.getCustomerId());
    }
}
