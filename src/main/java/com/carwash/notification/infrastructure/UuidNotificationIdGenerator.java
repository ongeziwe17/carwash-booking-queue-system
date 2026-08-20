package com.carwash.notification.infrastructure;

import com.carwash.notification.application.NotificationIdGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("postgres")
public final class UuidNotificationIdGenerator implements NotificationIdGenerator {

    @Override
    public String nextId() {
        return "notification:" + UUID.randomUUID();
    }
}
