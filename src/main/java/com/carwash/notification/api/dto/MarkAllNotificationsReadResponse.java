package com.carwash.notification.api.dto;

import java.time.LocalDateTime;

public record MarkAllNotificationsReadResponse(int affectedCount, LocalDateTime readAt) {
}
