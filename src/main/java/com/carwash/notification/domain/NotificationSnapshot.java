package com.carwash.notification.domain;

import java.time.LocalDateTime;

/** Immutable, bounded notification projection used by public read and mutation paths. */
public record NotificationSnapshot(
        String notificationId,
        String userId,
        String bookingId,
        String branchId,
        String serviceOfferingId,
        String type,
        String message,
        String channel,
        LocalDateTime sentAt,
        LocalDateTime readAt,
        DeliveryStatus deliveryStatus
) {
}
