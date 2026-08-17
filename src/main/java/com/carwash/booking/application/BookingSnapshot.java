package com.carwash.booking.application;

import com.carwash.booking.domain.BookingStatus;

import java.time.LocalDateTime;

/** Immutable, detached booking projection published to other capabilities. */
public record BookingSnapshot(
        String bookingId,
        String userId,
        String vehicleId,
        String branchId,
        String serviceOfferingId,
        String serviceId,
        LocalDateTime scheduledDateTime,
        BookingStatus status,
        LocalDateTime createdAt
) {
}
