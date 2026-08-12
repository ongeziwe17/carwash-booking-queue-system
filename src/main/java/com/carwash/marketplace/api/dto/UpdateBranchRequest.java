package com.carwash.marketplace.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Locale;

public record UpdateBranchRequest(
        @NotBlank @Size(max = 120) String branchName,
        @NotBlank @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @NotBlank @Size(max = 120) String city,
        @NotBlank @Size(max = 120) String province,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must contain two letters") String countryCode,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer = 2, fraction = 8) BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer = 3, fraction = 8) BigDecimal longitude,
        @NotBlank @Size(max = 64) String timezone,
        @NotNull Boolean publicDiscoveryEnabled
) {
    public UpdateBranchRequest {
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
