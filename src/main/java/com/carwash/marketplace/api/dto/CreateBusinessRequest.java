package com.carwash.marketplace.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record CreateBusinessRequest(
        @NotBlank @Size(max = 64) String businessId,
        @NotBlank @Size(max = 120) String businessName,
        @NotBlank @Email @Size(max = 254) String contactEmail,
        @NotBlank @Size(max = 32)
        @Pattern(regexp = "^[0-9+() .-]{7,32}$", message = "must be a valid phone number")
        String contactPhone,
        @Size(max = 64) String registrationNumber
) {
    public CreateBusinessRequest {
        businessId = trim(businessId);
        businessName = trim(businessName);
        contactEmail = normalizeEmail(contactEmail);
        contactPhone = trim(contactPhone);
        registrationNumber = trimToNull(registrationNumber);
    }

    private static String normalizeEmail(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
