package com.bank.payments.repository;

import com.bank.payments.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByFromAccount_AccountNumberOrToAccount_AccountNumberOrderByTransactionTimeDesc(
            String fromAccountNumber, String toAccountNumber);

    // Total sent out of the account by successful transfers since the given time
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t"
            + " WHERE t.fromAccount.accountNumber = :accountNumber"
            + " AND t.transactionType = com.bank.payments.model.Transaction.TransactionType.TRANSFER"
            + " AND t.status = com.bank.payments.model.Transaction.TransactionStatus.SUCCESS"
            + " AND t.transactionTime >= :since")
    BigDecimal sumTransferredSince(@Param("accountNumber") String accountNumber,
                                   @Param("since") LocalDateTime since);
}
