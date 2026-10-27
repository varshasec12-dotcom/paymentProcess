package com.bank.payments.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCustomerRequest(
        @NotBlank(message = "First name is required") String firstName,
        @NotBlank(message = "Last name is required") String lastName,
        @NotBlank(message = "Email is required") String email,
        @NotBlank(message = "Phone number is required") String phoneNumber
) {
}
