package com.bank.payments.service;

import com.bank.payments.model.ContactChangeRequest.ContactType;
import com.bank.payments.util.MaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Development stand-in: writes messages to the application log instead of sending them.
// Replace with a real SMS/email provider (Twilio, AWS SNS, SMTP, ...) before going live,
// and stop logging OTPs.
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void sendOtp(ContactType contactType, String destination, String otp, long validityMinutes) {
        log.info("[{}] To {}: Your OTP to update your {} is {}. It is valid for {} minutes. Do not share it.",
                channel(contactType), destination, label(contactType), otp, validityMinutes);
    }

    public void sendContactChangedAlert(ContactType contactType, String oldDestination, String newValue) {
        String masked = contactType == ContactType.EMAIL
                ? MaskingUtil.maskEmail(newValue)
                : MaskingUtil.maskPhoneNumber(newValue);
        log.info("[{}] To {}: Your {} was changed to {}. If this wasn't you, contact the bank immediately.",
                channel(contactType), oldDestination, label(contactType), masked);
    }

    private String channel(ContactType contactType) {
        return contactType == ContactType.EMAIL ? "EMAIL" : "SMS";
    }

    private String label(ContactType contactType) {
        return contactType == ContactType.EMAIL ? "email address" : "mobile number";
    }
}
