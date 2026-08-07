package com.carwash.service;

import com.carwash.domain.Booking;
import com.carwash.domain.Notification;
import com.carwash.domain.User;
import com.carwash.repository.NotificationRepository;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class NotificationManagementService {

    private static final String DEFAULT_CHANNEL = "IN_APP";
    private static final int DEFAULT_RECENT_LIMIT = 10;
    private static final AtomicLong NOTIFICATION_SEQUENCE = new AtomicLong();

    private final NotificationRepository notificationRepository;

    public NotificationManagementService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Notification createNotification(User user, Booking booking, String type, String message) {
        if (user == null || user.getUserId() == null || user.getUserId().isBlank()) {
            throw new BusinessRuleViolationException("Notification user is required");
        }
        if (message == null || message.isBlank()) {
            throw new BusinessRuleViolationException("Notification message is required");
        }

        Notification notification = new Notification(nextNotificationId(), user, booking, type, message, DEFAULT_CHANNEL);
        notification.send();
        notificationRepository.save(notification);
        return notification;
    }

    public List<Notification> findByUserId(String userId) {
        validateUserId(userId);
        return notificationRepository.findByUserId(userId);
    }

    public List<Notification> findRecentByUserId(String userId) {
        return findRecentByUserId(userId, DEFAULT_RECENT_LIMIT);
    }

    public List<Notification> findRecentByUserId(String userId, int limit) {
        validateUserId(userId);
        if (limit <= 0) throw new BusinessRuleViolationException("Notification limit must be positive");

        return notificationRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(Notification::getSentAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Notification::getNotificationId, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .limit(limit)
                .toList();
    }

    public Notification markAsRead(String notificationId) {
        if (notificationId == null || notificationId.isBlank()) {
            throw new BusinessRuleViolationException("Notification ID is required");
        }
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        notification.markAsRead();
        notificationRepository.save(notification);
        return notification;
    }

    private String nextNotificationId() {
        return "notification-" + String.format("%020d", NOTIFICATION_SEQUENCE.incrementAndGet());
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessRuleViolationException("User ID is required");
        }
    }
}