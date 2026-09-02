package com.carwash.notification.application;

import java.time.LocalDateTime;
import java.util.Objects;

public record MarkAllNotificationsReadResult(int affectedCount, LocalDateTime readAt) {
    public MarkAllNotificationsReadResult {
        if (affectedCount < 0) throw new IllegalArgumentException("Affected count cannot be negative");
        Objects.requireNonNull(readAt, "Notification read timestamp is required");
    }
}
