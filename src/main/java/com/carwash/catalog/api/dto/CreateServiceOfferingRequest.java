package com.carwash.catalog.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateServiceOfferingRequest(
        @NotBlank @Size(max = 64) String offeringId,
        @NotBlank @Size(max = 64) String serviceId,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull @Min(1) @Max(1440) Integer estimatedDurationMin,
        @NotNull @Min(1) @Max(1000) Integer concurrentCapacity
) {
    public CreateServiceOfferingRequest {
        offeringId = trim(offeringId);
        serviceId = trim(serviceId);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
