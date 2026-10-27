package com.bank.payments.dto;

import com.bank.payments.model.ContactChangeRequest.ContactType;

import java.time.LocalDateTime;

public record ContactChangeResponse(
        Long requestId,
        ContactType contactType,
        String otpSentTo,
        LocalDateTime expiresAt,
        String message
) {
}
