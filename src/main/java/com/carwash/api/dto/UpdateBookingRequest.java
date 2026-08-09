package com.carwash.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBookingRequest(
        @NotBlank(message = "vehicleId is required") @Size(max = 64) String vehicleId,
        @NotBlank(message = "serviceId is required") @Size(max = 64) String serviceId,
        @Size(max = 1000) String specialRequest
) {
    public UpdateBookingRequest {
        vehicleId = trim(vehicleId);
        serviceId = trim(serviceId);
        specialRequest = trim(specialRequest);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
