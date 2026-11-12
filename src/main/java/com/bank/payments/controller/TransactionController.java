package com.bank.payments.controller;

import com.bank.payments.model.Transaction;
import com.bank.payments.security.CustomerPrincipal;
import com.bank.payments.service.AccountService;
import com.bank.payments.service.TransactionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final AccountService accountService;

    public TransactionController(TransactionService transactionService, AccountService accountService) {
        this.transactionService = transactionService;
        this.accountService = accountService;
    }

    @GetMapping("/account/{accountNumber}")
    public List<Transaction> getTransactionsByAccount(@AuthenticationPrincipal CustomerPrincipal principal,
                                                      @PathVariable String accountNumber) {
        requireAccess(principal, accountNumber);
        return transactionService.getTransactionsByAccount(accountNumber);
    }

    @GetMapping("/account/{accountNumber}/mini-statement")
    public List<Transaction> getMiniStatement(@AuthenticationPrincipal CustomerPrincipal principal,
                                              @PathVariable String accountNumber) {
        requireAccess(principal, accountNumber);
        return transactionService.getMiniStatement(accountNumber);
    }

    // Customers only see statements for their own accounts; bank staff see any
    private void requireAccess(CustomerPrincipal principal, String accountNumber) {
        if (!principal.isAdmin()) {
            accountService.findOwnedAccount(accountNumber, principal.getCustomerId());
        }
    }
}
