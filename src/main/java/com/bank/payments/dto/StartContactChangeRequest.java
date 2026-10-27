package com.bank.payments.dto;

import com.bank.payments.model.ContactChangeRequest.ContactType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StartContactChangeRequest(
        @NotNull(message = "Contact type is required (EMAIL or PHONE)") ContactType contactType,
        @NotBlank(message = "New value is required") String newValue
) {
}
