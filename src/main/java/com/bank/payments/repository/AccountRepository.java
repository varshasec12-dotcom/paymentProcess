package com.bank.payments.repository;

import com.bank.payments.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {

    List<Account> findByCustomer_CustomerIdOrderByAccountNumber(Long customerId);

    boolean existsByCustomer_CustomerIdAndAccountType(Long customerId, String accountType);

    // SELECT ... FOR UPDATE: other transactions that want to change this account wait until we
    // commit, so two payments can never both read the same old balance. Must run inside a transaction.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountNumber = :accountNumber")
    Optional<Account> findByIdForUpdate(@Param("accountNumber") String accountNumber);

    // PostgreSQL sequences never hand out the same value twice, even to concurrent requests
    @Query(value = "select nextval('account_number_seq')", nativeQuery = true)
    long nextAccountNumber();
}
