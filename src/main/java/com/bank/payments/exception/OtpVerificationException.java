package com.bank.payments.exception;

public class OtpVerificationException extends RuntimeException {

    public OtpVerificationException(String message) {
        super(message);
    }
}
