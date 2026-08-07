package com.carwash.repository.inmemory;

import com.carwash.domain.Notification;
import com.carwash.repository.NotificationRepository;

import java.util.List;

public class InMemoryNotificationRepository extends InMemoryRepository<Notification, String> implements NotificationRepository {
    @Override
    public List<Notification> findByUserId(String userId) {
        return storage.values().stream().filter(notification -> notification.getUser() != null && notification.getUser().getUserId().equals(userId)).toList();
    }

    @Override
    protected String getId(Notification entity) {
        return entity.getNotificationId();
    }
}
