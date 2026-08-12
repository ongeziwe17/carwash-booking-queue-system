package com.carwash.catalog.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateServiceRequest(
        @NotBlank @Size(max = 64) String serviceId,
        @NotBlank @Size(max = 120) String serviceName,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull @Min(1) @Max(1440) Integer estimatedDurationMin
) {
    public CreateServiceRequest {
        serviceId = trim(serviceId);
        serviceName = trim(serviceName);
        description = trim(description);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
