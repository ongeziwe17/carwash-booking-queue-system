package com.carwash.notification.domain;

import com.carwash.shared.domain.Repository;

import com.carwash.notification.domain.Notification;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends Repository<Notification, String> {
    List<Notification> findByUserId(String userId);
    List<Notification> findByBookingId(String bookingId);
    List<Notification> findByUserIdAndBusinessId(String userId, String businessId);
    List<Notification> findByBusinessId(String businessId);
    Optional<Notification> findByIdAndBusinessId(String notificationId, String businessId);

    int deleteByUserId(String userId);
    int deleteByBookingId(String bookingId);
    int deleteByBookingIdAndBusinessId(String bookingId, String businessId);
    int deleteByBookingIdAndUserId(String bookingId, String userId);
    int deleteByBookingIdForAdministrator(String bookingId);
}
