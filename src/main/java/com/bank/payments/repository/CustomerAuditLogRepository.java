package com.bank.payments.repository;

import com.bank.payments.model.CustomerAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerAuditLogRepository extends JpaRepository<CustomerAuditLog, Long> {

    List<CustomerAuditLog> findByCustomer_CustomerIdOrderByChangedAtDesc(Long customerId);
}
