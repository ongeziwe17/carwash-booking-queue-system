package com.carwash.notification.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/** Exact keyset position for the newest-first notification inbox order. */
public record NotificationCursor(LocalDateTime sentAt, String notificationId) {
    public NotificationCursor {
        Objects.requireNonNull(sentAt, "Notification cursor timestamp is required");
        if (notificationId == null || notificationId.isBlank()) {
            throw new IllegalArgumentException("Notification cursor ID is required");
        }
    }
}
