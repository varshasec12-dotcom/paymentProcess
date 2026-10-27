package com.bank.payments.repository;

import com.bank.payments.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByFromAccount_AccountNumberOrToAccount_AccountNumberOrderByTransactionTimeDesc(
            String fromAccountNumber, String toAccountNumber);
}
