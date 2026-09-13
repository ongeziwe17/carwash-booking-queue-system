package com.carwash.notification.domain;

import java.util.List;

/** Page candidates and the complete unread total observed from one repository snapshot. */
public record NotificationInboxSnapshot(
        List<NotificationSnapshot> notifications,
        long unreadCount
) {
    public NotificationInboxSnapshot {
        notifications = List.copyOf(notifications);
    }
}
