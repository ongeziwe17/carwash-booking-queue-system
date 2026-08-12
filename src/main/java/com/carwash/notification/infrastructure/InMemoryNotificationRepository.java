package com.carwash.notification.infrastructure;

import com.carwash.shared.infrastructure.InMemoryRepository;

import com.carwash.notification.domain.Notification;
import com.carwash.notification.domain.NotificationRepository;

import java.util.List;

public class InMemoryNotificationRepository extends InMemoryRepository<Notification, String>
        implements NotificationRepository {

    @Override
    public List<Notification> findByUserId(String userId) {
        return findMatching(notification -> notification.getUser() != null
                && userId.equals(notification.getUser().getUserId()));
    }

    @Override
    public List<Notification> findByBookingId(String bookingId) {
        return findMatching(notification -> notification.getBooking() != null
                && bookingId.equals(notification.getBooking().getBookingId()));
    }

    @Override
    public int deleteByUserId(String userId) {
        return deleteMatching(notification -> notification.getUser() != null
                && userId.equals(notification.getUser().getUserId()));
    }

    @Override
    public int deleteByBookingId(String bookingId) {
        return deleteMatching(notification -> notification.getBooking() != null
                && bookingId.equals(notification.getBooking().getBookingId()));
    }

    @Override
    protected String getId(Notification entity) {
        return entity.getNotificationId();
    }
}
