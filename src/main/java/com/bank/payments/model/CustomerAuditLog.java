package com.bank.payments.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "customer_audit_log")
public class CustomerAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditId;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    private AuditAction action;

    private String oldValue;
    private String newValue;
    private LocalDateTime changedAt;

    public enum AuditAction {
        EMAIL_CHANGED,
        PHONE_CHANGED,
        CUSTOMER_DEACTIVATED
    }

    protected CustomerAuditLog() {
    }

    public CustomerAuditLog(Customer customer, AuditAction action, String oldValue,
                            String newValue, LocalDateTime changedAt) {
        this.customer = customer;
        this.action = action;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changedAt = changedAt;
    }

    public Long getAuditId() {
        return auditId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
