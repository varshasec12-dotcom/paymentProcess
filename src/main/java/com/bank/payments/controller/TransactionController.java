package com.bank.payments.controller;

import com.bank.payments.model.Transaction;
import com.bank.payments.service.TransactionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/account/{accountNumber}")
    public List<Transaction> getTransactionsByAccount(@PathVariable String accountNumber) {
        return transactionService.getTransactionsByAccount(accountNumber);
    }

    @GetMapping("/account/{accountNumber}/mini-statement")
    public List<Transaction> getMiniStatement(@PathVariable String accountNumber) {
        return transactionService.getMiniStatement(accountNumber);
    }
}
