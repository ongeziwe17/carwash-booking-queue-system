package com.carwash.notification.api.dto;

import com.carwash.notification.domain.DeliveryStatus;

import java.time.LocalDateTime;

/** Bounded notification representation without user or booking aggregate graphs. */
public record NotificationResponse(
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
