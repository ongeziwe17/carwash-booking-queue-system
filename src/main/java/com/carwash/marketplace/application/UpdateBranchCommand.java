package com.carwash.marketplace.application;

import java.math.BigDecimal;
import java.util.Locale;

public record UpdateBranchCommand(
        String branchName,
        String addressLine1,
        String addressLine2,
        String city,
        String province,
        String postalCode,
        String countryCode,
        BigDecimal latitude,
        BigDecimal longitude,
        String timezone,
        boolean publicDiscoveryEnabled
) {
    public UpdateBranchCommand {
        branchName = trim(branchName);
        addressLine1 = trim(addressLine1);
        addressLine2 = trimToNull(addressLine2);
        city = trim(city);
        province = trim(province);
        postalCode = trim(postalCode);
        countryCode = normalizeCountryCode(countryCode);
        timezone = trim(timezone);
    }

    private static String normalizeCountryCode(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
