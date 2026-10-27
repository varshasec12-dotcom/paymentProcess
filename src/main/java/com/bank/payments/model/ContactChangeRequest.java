package com.bank.payments.model;

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
@Table(name = "contact_change_requests")
public class ContactChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requestId;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    private ContactType contactType;

    private String oldValue;
    private String newValue;

    // BCrypt hash of the OTP; the plain OTP is never stored
    private String otpHash;

    private int failedAttempts;

    @Enumerated(EnumType.STRING)
    private RequestStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;

    public enum ContactType {
        EMAIL,
        PHONE
    }

    public enum RequestStatus {
        PENDING,
        VERIFIED,
        EXPIRED,
        FAILED,
        CANCELLED
    }

    protected ContactChangeRequest() {
    }

    public ContactChangeRequest(Customer customer, ContactType contactType, String oldValue,
                                String newValue, String otpHash, LocalDateTime createdAt,
                                LocalDateTime expiresAt) {
        this.customer = customer;
        this.contactType = contactType;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.otpHash = otpHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = RequestStatus.PENDING;
    }

    public Long getRequestId() {
        return requestId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public ContactType getContactType() {
        return contactType;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getOtpHash() {
        return otpHash;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void incrementFailedAttempts() {
        this.failedAttempts++;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
