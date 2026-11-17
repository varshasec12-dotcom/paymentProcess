package com.bank.payments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Locale;

public record RegisterRequest(
        @NotBlank(message = "First name is required") String firstName,
        @NotBlank(message = "Last name is required") String lastName,
        @NotBlank(message = "Email is required") String email,
        @NotBlank(message = "Phone number is required") String phoneNumber,
        @NotBlank(message = "Password is required")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$",
                message = "Password must be 8-64 characters with an uppercase letter, a lowercase letter, "
                        + "a number and a special character") String password,
        @NotBlank(message = "Please confirm your password") String confirmPassword,
        // The first account, opened together with the customer. Same rule as CreateAccountRequest.
        @NotBlank(message = "Account type is required")
        @Pattern(regexp = "SAVINGS|CURRENT|SALARY", message = "Account type must be SAVINGS, CURRENT or SALARY")
        String accountType
) {
    // Accept any case ("salary", "Salary") but always store the type in capitals
    public RegisterRequest {
        if (accountType != null) {
            accountType = accountType.toUpperCase(Locale.ROOT);
        }
    }
}
