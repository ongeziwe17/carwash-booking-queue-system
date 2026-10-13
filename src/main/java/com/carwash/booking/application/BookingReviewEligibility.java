package com.carwash.booking.application;

import com.carwash.booking.domain.BookingStatus;
import java.time.LocalDateTime;

/** Narrow trusted booking association; excludes vehicle and customer contact details. */
public record BookingReviewEligibility(String bookingId, String customerId, String branchId,
        String offeringId, String serviceId, LocalDateTime scheduledAt, BookingStatus status) { }
