package com.carwash.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record UpdateBookingRequest(
        @NotBlank(message = "vehicleId is required")
        String vehicleId,

        @NotBlank(message = "serviceId is required")
        String serviceId,

        @NotNull(message = "scheduledDateTime is required")
        @Future(message = "scheduledDateTime must be in the future")
        LocalDateTime scheduledDateTime,

        String specialRequest
) {
}
