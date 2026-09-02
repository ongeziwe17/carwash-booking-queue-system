package com.carwash.notification.domain;

import com.carwash.shared.domain.Repository;

import com.carwash.notification.domain.Notification;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface NotificationRepository extends Repository<Notification, String> {
    List<Notification> findByUserId(String userId);
    List<Notification> findByBookingId(String bookingId);
    List<Notification> findByUserIdAndBusinessId(String userId, String businessId);
    List<Notification> findByBusinessId(String businessId);
    Optional<Notification> findByIdAndBusinessId(String notificationId, String businessId);

    List<NotificationSnapshot> findPageByUserId(
            String userId, boolean unreadOnly, NotificationCursor cursor, int limit);
    List<NotificationSnapshot> findPageByUserIdAndBusinessId(
            String userId, String businessId, boolean unreadOnly, NotificationCursor cursor, int limit);
    long countUnreadByUserId(String userId);
    long countUnreadByUserIdAndBusinessId(String userId, String businessId);
    Optional<NotificationSnapshot> findSnapshotByIdAndUserId(String notificationId, String userId);
    Optional<NotificationSnapshot> markAsReadByUserId(
            String notificationId, String userId, LocalDateTime readAt);
    int markAllAsReadByUserId(String userId, LocalDateTime readAt);

    int deleteByUserId(String userId);
    int deleteByBookingId(String bookingId);
    int deleteByBookingIdAndBusinessId(String bookingId, String businessId);
    int deleteByBookingIdAndUserId(String bookingId, String userId);
    int deleteByBookingIdForAdministrator(String bookingId);
}
