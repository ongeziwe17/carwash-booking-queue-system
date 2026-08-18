package com.carwash.booking.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record RescheduleBookingRequest(
        @NotNull(message = "scheduledDateTime is required") LocalDateTime scheduledDateTime
) {
}
