package com.bank.payments.service;

import com.bank.payments.model.ContactChangeRequest.ContactType;
import com.bank.payments.util.MaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Development stand-in: writes messages to the application log instead of sending them.
// Replace with a real SMS/email provider (Twilio, AWS SNS, SMTP, ...) before going live.
// Contact details are always masked. The OTP itself is only logged when
// payments.notifications.log-otp=true, which is meant for local development only.
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final boolean logOtp;

    public NotificationService(@Value("${payments.notifications.log-otp:false}") boolean logOtp) {
        this.logOtp = logOtp;
        if (logOtp) {
            log.warn("payments.notifications.log-otp is enabled: OTPs will be written to the log. "
                    + "Never enable this outside local development.");
        }
    }

    public void sendOtp(ContactType contactType, String destination, String otp, long validityMinutes) {
        log.info("[{}] To {}: Your OTP to update your {} is {}. It is valid for {} minutes. Do not share it.",
                channel(contactType), mask(contactType, destination), label(contactType),
                logOtp ? otp : "******", validityMinutes);
    }

    public void sendContactChangedAlert(ContactType contactType, String oldDestination, String newValue) {
        log.info("[{}] To {}: Your {} was changed to {}. If this wasn't you, contact the bank immediately.",
                channel(contactType), mask(contactType, oldDestination), label(contactType),
                mask(contactType, newValue));
    }

    private String mask(ContactType contactType, String value) {
        return contactType == ContactType.EMAIL
                ? MaskingUtil.maskEmail(value)
                : MaskingUtil.maskPhoneNumber(value);
    }

    private String channel(ContactType contactType) {
        return contactType == ContactType.EMAIL ? "EMAIL" : "SMS";
    }

    private String label(ContactType contactType) {
        return contactType == ContactType.EMAIL ? "email address" : "mobile number";
    }
}
