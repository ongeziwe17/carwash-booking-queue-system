package com.carwash.booking.application;

import com.carwash.booking.domain.BookingStatus;
import java.time.LocalDate;

/** Current booking status grouped by branch-local scheduled date, hour and offering. */
public record BookingReportBucket(LocalDate date, int hour, String offeringId,
                                  BookingStatus status, long count) { }
