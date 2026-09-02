package com.carwash.notification.api.dto;

import java.util.List;

public record NotificationInboxResponse(
        List<NotificationResponse> notifications,
        long unreadCount,
        String nextCursor
) {
    public NotificationInboxResponse {
        notifications = List.copyOf(notifications);
    }
}
