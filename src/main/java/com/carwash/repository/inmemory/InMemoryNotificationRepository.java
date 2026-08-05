package com.carwash.repository.inmemory;

import com.carwash.domain.Notification;
import com.carwash.repository.NotificationRepository;

import java.util.List;

public class InMemoryNotificationRepository extends InMemoryRepository<Notification, String>
        implements NotificationRepository {

    @Override
    public List<Notification> findByUserId(String userId) {
        return immutableSorted(storage.values().stream()
                .filter(notification -> notification.getUser() != null
                        && userId.equals(notification.getUser().getUserId()))
                .toList());
    }

    @Override
    public List<Notification> findByBookingId(String bookingId) {
        return immutableSorted(storage.values().stream()
                .filter(notification -> notification.getBooking() != null
                        && bookingId.equals(notification.getBooking().getBookingId()))
                .toList());
    }

    @Override
    public synchronized int deleteByUserId(String userId) {
        return deleteMatching(findByUserId(userId));
    }

    @Override
    public synchronized int deleteByBookingId(String bookingId) {
        return deleteMatching(findByBookingId(bookingId));
    }

    private int deleteMatching(List<Notification> notifications) {
        int deleted = 0;
        for (Notification notification : notifications) {
            if (deleteById(notification.getNotificationId())) {
                deleted++;
            }
        }
        return deleted;
    }

    @Override
    protected String getId(Notification entity) {
        return entity.getNotificationId();
    }
}
