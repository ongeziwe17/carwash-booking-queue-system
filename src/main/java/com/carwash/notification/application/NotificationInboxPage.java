package com.carwash.notification.application;

import com.carwash.notification.domain.NotificationSnapshot;

import java.util.List;

public record NotificationInboxPage(
        List<NotificationSnapshot> notifications,
        long unreadCount,
        String nextCursor
) {
    public NotificationInboxPage {
        notifications = List.copyOf(notifications);
    }
}
