package com.bank.payments.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank(message = "Source account number is required") String fromAccountNumber,
        @NotBlank(message = "Destination account number is required") String toAccountNumber,
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        @Digits(integer = 17, fraction = 2, message = "Amount can have at most 2 decimal places") BigDecimal amount
) {
}
