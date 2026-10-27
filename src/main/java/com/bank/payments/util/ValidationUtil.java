package com.bank.payments.util;

import java.util.regex.Pattern;

public final class ValidationUtil {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    // Accepts an optional leading '+' followed by 10-15 digits, after stripping
    // common formatting characters (spaces, dashes, dots, parentheses).
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{10,15}$");

    private ValidationUtil() {
    }

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber != null && PHONE_PATTERN.matcher(normalizePhoneNumber(phoneNumber)).matches();
    }

    // Stored form of a phone number, so "98765 43210" and "9876543210" compare equal.
    public static String normalizePhoneNumber(String phoneNumber) {
        return phoneNumber.trim().replaceAll("[\\s().-]", "");
    }
}
