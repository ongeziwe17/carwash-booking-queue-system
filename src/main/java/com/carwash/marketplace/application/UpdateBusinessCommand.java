package com.carwash.marketplace.application;

public record UpdateBusinessCommand(
        String businessName,
        String contactEmail,
        String contactPhone,
        String registrationNumber
) {
    public UpdateBusinessCommand {
        businessName = trim(businessName);
        contactEmail = normalizeEmail(contactEmail);
        contactPhone = trim(contactPhone);
        registrationNumber = trimToNull(registrationNumber);
    }

    private static String normalizeEmail(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toLowerCase(java.util.Locale.ROOT);
    }

    private static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
