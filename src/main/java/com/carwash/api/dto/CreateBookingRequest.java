package com.carwash.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateBookingRequest(
        @NotBlank(message = "bookingId is required") @Size(max = 64) String bookingId,
        @NotBlank(message = "userId is required") @Size(max = 64) String userId,
        @NotBlank(message = "vehicleId is required") @Size(max = 64) String vehicleId,
        @NotBlank(message = "serviceId is required") @Size(max = 64) String serviceId,
        @NotNull(message = "scheduledDateTime is required")
        @Future(message = "scheduledDateTime must be in the future") LocalDateTime scheduledDateTime,
        @Size(max = 1000) String specialRequest
) {
    public CreateBookingRequest {
        bookingId = trim(bookingId);
        userId = trim(userId);
        vehicleId = trim(vehicleId);
        serviceId = trim(serviceId);
        specialRequest = trim(specialRequest);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
