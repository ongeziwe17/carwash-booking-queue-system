package com.carwash.booking.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateBookingRequest(
        @NotBlank(message = "bookingId is required") @Size(max = 64) String bookingId,
        @NotBlank(message = "userId is required") @Size(max = 64) String userId,
        @NotBlank(message = "vehicleId is required") @Size(max = 64) String vehicleId,
        @NotBlank(message = "branchId is required") @Size(max = 64) String branchId,
        @NotBlank(message = "serviceOfferingId is required") @Size(max = 64) String serviceOfferingId,
        @NotNull(message = "scheduledDateTime is required") LocalDateTime scheduledDateTime,
        @Size(max = 1000) String specialRequest
) {
    public CreateBookingRequest {
        bookingId = trim(bookingId);
        userId = trim(userId);
        vehicleId = trim(vehicleId);
        branchId = trim(branchId);
        serviceOfferingId = trim(serviceOfferingId);
        specialRequest = trim(specialRequest);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
