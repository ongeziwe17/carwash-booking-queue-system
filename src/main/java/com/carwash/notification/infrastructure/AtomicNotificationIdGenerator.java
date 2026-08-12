package com.carwash.notification.infrastructure;

import com.carwash.notification.application.NotificationIdGenerator;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Production notification ID generator for the current single-JVM runtime.
 */
public final class AtomicNotificationIdGenerator implements NotificationIdGenerator {

    private final AtomicLong sequence = new AtomicLong();

    @Override
    public String nextId() {
        return "notification-" + String.format("%020d", sequence.incrementAndGet());
    }
}
