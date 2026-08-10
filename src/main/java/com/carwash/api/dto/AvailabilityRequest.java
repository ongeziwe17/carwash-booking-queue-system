package com.carwash.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record AvailabilityRequest(
        @Parameter(description = "Required service identifier", required = true, example = "service-001")
        @NotBlank(message = "serviceId is required") @Size(max = 64) String serviceId,
        @Parameter(description = "Required ISO YYYY-MM-DD availability date", required = true,
                example = "2090-01-15")
        @NotNull(message = "date is required")
        @FutureOrPresent(message = "date must be today or in the future")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
) {
    public AvailabilityRequest {
        serviceId = serviceId == null ? null : serviceId.trim();
    }
}
