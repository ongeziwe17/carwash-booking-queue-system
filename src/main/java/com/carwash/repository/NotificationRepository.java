package com.carwash.repository;

import com.carwash.domain.Notification;

import java.util.List;

public interface NotificationRepository extends Repository<Notification, String> {
    List<Notification> findByUserId(String userId);
    List<Notification> findByBookingId(String bookingId);

    int deleteByUserId(String userId);
    int deleteByBookingId(String bookingId);
}
