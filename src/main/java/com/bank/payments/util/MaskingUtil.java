package com.bank.payments.util;

public final class MaskingUtil {

    private MaskingUtil() {
    }

    // "varsha@example.com" -> "va****@example.com"
    public static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "****";
        }
        int visible = Math.min(2, at);
        return email.substring(0, visible) + "****" + email.substring(at);
    }

    // "9876543210" -> "******3210"
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber.length() <= 4) {
            return "****";
        }
        return "******" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
