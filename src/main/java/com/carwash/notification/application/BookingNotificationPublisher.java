package com.carwash.notification.application;

import com.carwash.booking.domain.Booking;
import com.carwash.notification.domain.Notification;

/**
 * Narrow trusted contract for a workflow that already holds an authorized canonical booking.
 * Implementations must not reload that booking through an unscoped identifier lookup.
 */
public interface BookingNotificationPublisher {

    Notification publishForAuthorizedBooking(Booking authorizedBooking, String type, String message);
}
