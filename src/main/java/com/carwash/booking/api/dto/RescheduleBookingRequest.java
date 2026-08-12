package com.carwash.booking.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record RescheduleBookingRequest(
        @NotNull(message = "scheduledDateTime is required")
        @Future(message = "scheduledDateTime must be in the future") LocalDateTime scheduledDateTime
) {
}
