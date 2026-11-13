package com.bank.payments.service;

import com.bank.payments.dto.ContactChangeResponse;
import com.bank.payments.exception.ContactChangeRequestNotFoundException;
import com.bank.payments.exception.DuplicateContactException;
import com.bank.payments.exception.OtpVerificationException;
import com.bank.payments.model.ContactChangeRequest;
import com.bank.payments.model.ContactChangeRequest.ContactType;
import com.bank.payments.model.ContactChangeRequest.RequestStatus;
import com.bank.payments.model.Customer;
import com.bank.payments.model.CustomerAuditLog;
import com.bank.payments.model.CustomerAuditLog.AuditAction;
import com.bank.payments.repository.ContactChangeRequestRepository;
import com.bank.payments.repository.CustomerAuditLogRepository;
import com.bank.payments.repository.CustomerRepository;
import com.bank.payments.util.MaskingUtil;
import com.bank.payments.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class ContactChangeService {

    private static final Logger log = LoggerFactory.getLogger(ContactChangeService.class);

    private final ContactChangeRequestRepository requestRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAuditLogRepository auditLogRepository;
    private final CustomerService customerService;
    private final NotificationService notificationService;
    private final long otpValidityMinutes;
    private final int maxOtpAttempts;

    private final PasswordEncoder otpEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public ContactChangeService(ContactChangeRequestRepository requestRepository,
                                CustomerRepository customerRepository,
                                CustomerAuditLogRepository auditLogRepository,
                                CustomerService customerService,
                                NotificationService notificationService,
                                @Value("${payments.contact-change.otp-validity-minutes}") long otpValidityMinutes,
                                @Value("${payments.contact-change.max-otp-attempts}") int maxOtpAttempts) {
        this.requestRepository = requestRepository;
        this.customerRepository = customerRepository;
        this.auditLogRepository = auditLogRepository;
        this.customerService = customerService;
        this.notificationService = notificationService;
        this.otpValidityMinutes = otpValidityMinutes;
        this.maxOtpAttempts = maxOtpAttempts;
    }

    @Transactional
    public ContactChangeResponse requestChange(Long customerId, ContactType contactType, String newValue) {
        Customer customer = customerService.findCustomerById(customerId);
        String normalized = normalize(contactType, newValue);
        String current = currentValue(customer, contactType);

        if (normalized.equalsIgnoreCase(current)) {
            throw new IllegalArgumentException("New value is the same as the current one.");
        }
        ensureNotInUse(contactType, normalized, customerId);

        // Only one open request per contact type: a new request replaces any earlier one
        requestRepository.findByCustomer_CustomerIdAndContactTypeAndStatus(
                        customerId, contactType, RequestStatus.PENDING)
                .forEach(previous -> previous.setStatus(RequestStatus.CANCELLED));

        String otp = String.format("%06d", random.nextInt(1_000_000));
        LocalDateTime now = LocalDateTime.now();
        ContactChangeRequest request = requestRepository.save(new ContactChangeRequest(
                customer, contactType, current, normalized, otpEncoder.encode(otp),
                now, now.plusMinutes(otpValidityMinutes)));

        // The OTP goes to the NEW contact, proving the customer actually owns it
        notificationService.sendOtp(contactType, normalized, otp, otpValidityMinutes);
        log.info("Contact change requested: customerId={}, type={}, requestId={}",
                customerId, contactType, request.getRequestId());

        return new ContactChangeResponse(request.getRequestId(), contactType, mask(contactType, normalized),
                request.getExpiresAt(), "OTP sent. Verify it to complete the change.");
    }

    // Failed attempts and expiry must be saved even though an exception is thrown
    @Transactional(noRollbackFor = OtpVerificationException.class)
    public Customer verifyChange(Long customerId, Long requestId, String otp) {
        Customer customer = customerService.findCustomerById(customerId);
        ContactChangeRequest request = requestRepository.findByRequestIdAndCustomer_CustomerId(requestId, customerId)
                .orElseThrow(() -> new ContactChangeRequestNotFoundException(
                        "Contact change request not found: " + requestId));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new OtpVerificationException("This request is no longer pending (status: "
                    + request.getStatus() + "). Please start a new request.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(request.getExpiresAt())) {
            request.setStatus(RequestStatus.EXPIRED);
            log.info("Contact change OTP expired: customerId={}, requestId={}", customerId, requestId);
            throw new OtpVerificationException("OTP has expired. Please start a new request.");
        }

        if (!otpEncoder.matches(otp, request.getOtpHash())) {
            request.incrementFailedAttempts();
            int remaining = maxOtpAttempts - request.getFailedAttempts();
            if (remaining <= 0) {
                request.setStatus(RequestStatus.FAILED);
                log.warn("Contact change blocked after {} incorrect OTPs: customerId={}, requestId={}",
                        request.getFailedAttempts(), customerId, requestId);
                throw new OtpVerificationException("Too many incorrect attempts. Please start a new request.");
            }
            log.warn("Incorrect contact change OTP: customerId={}, requestId={}, attempt={}/{}",
                    customerId, requestId, request.getFailedAttempts(), maxOtpAttempts);
            throw new OtpVerificationException("Incorrect OTP. " + remaining + " attempt(s) left.");
        }

        // The value may have been taken by someone else while this request was pending
        ensureNotInUse(request.getContactType(), request.getNewValue(), customerId);

        AuditAction action;
        if (request.getContactType() == ContactType.EMAIL) {
            customer.setEmail(request.getNewValue());
            action = AuditAction.EMAIL_CHANGED;
        } else {
            customer.setPhoneNumber(request.getNewValue());
            action = AuditAction.PHONE_CHANGED;
        }
        customer.setContactChangedAt(now);
        customerRepository.save(customer);

        request.setStatus(RequestStatus.VERIFIED);
        request.setCompletedAt(now);

        auditLogRepository.save(new CustomerAuditLog(customer, action,
                request.getOldValue(), request.getNewValue(), now));
        log.info("Contact changed: customerId={}, action={}, requestId={}", customerId, action, requestId);

        // Alert the OLD contact so the real owner notices an unauthorised change
        notificationService.sendContactChangedAlert(request.getContactType(),
                request.getOldValue(), request.getNewValue());

        return customer;
    }

    private String normalize(ContactType contactType, String value) {
        if (contactType == ContactType.EMAIL) {
            if (!ValidationUtil.isValidEmail(value)) {
                throw new IllegalArgumentException("Invalid email address: " + value);
            }
            return value.trim();
        }
        if (!ValidationUtil.isValidPhoneNumber(value)) {
            throw new IllegalArgumentException("Invalid phone number: " + value);
        }
        return ValidationUtil.normalizePhoneNumber(value);
    }

    private String currentValue(Customer customer, ContactType contactType) {
        return contactType == ContactType.EMAIL ? customer.getEmail() : customer.getPhoneNumber();
    }

    private void ensureNotInUse(ContactType contactType, String value, Long customerId) {
        boolean inUse = contactType == ContactType.EMAIL
                ? customerRepository.existsByEmailIgnoreCaseAndCustomerIdNot(value, customerId)
                : customerRepository.existsByPhoneNumberAndCustomerIdNot(value, customerId);
        if (inUse) {
            throw new DuplicateContactException(contactType == ContactType.EMAIL
                    ? "Email address is already registered to another customer."
                    : "Phone number is already registered to another customer.");
        }
    }

    private String mask(ContactType contactType, String value) {
        return contactType == ContactType.EMAIL
                ? MaskingUtil.maskEmail(value)
                : MaskingUtil.maskPhoneNumber(value);
    }
}
