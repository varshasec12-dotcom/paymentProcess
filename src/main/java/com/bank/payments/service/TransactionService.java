package com.bank.payments.service;

import com.bank.payments.model.Account;
import com.bank.payments.model.Transaction;
import com.bank.payments.model.Transaction.TransactionStatus;
import com.bank.payments.model.Transaction.TransactionType;
import com.bank.payments.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Transaction recordTransaction(Account fromAccount, Account toAccount,
                                          BigDecimal amount, TransactionType type) {
        Transaction transaction = new Transaction();
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setAmount(amount);
        transaction.setTransactionTime(LocalDateTime.now());
        transaction.setTransactionType(type);
        transaction.setStatus(TransactionStatus.SUCCESS);
        return transactionRepository.save(transaction);
    }

    public List<Transaction> getTransactionsByAccount(String accountNumber) {
        return transactionRepository
                .findByFromAccount_AccountNumberOrToAccount_AccountNumberOrderByTransactionTimeDesc(
                        accountNumber, accountNumber);
    }

    // Total sent out of the account by transfers since midnight today (server time)
    public BigDecimal transferredToday(String accountNumber) {
        return transactionRepository.sumTransferredSince(accountNumber, LocalDate.now().atStartOfDay());
    }

    public List<Transaction> getMiniStatement(String accountNumber) {
        return getTransactionsByAccount(accountNumber).stream()
                .limit(5)
                .toList();
    }
}
