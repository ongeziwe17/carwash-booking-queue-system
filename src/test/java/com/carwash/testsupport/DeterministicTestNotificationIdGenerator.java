package com.carwash.testsupport;

import com.carwash.notification.application.NotificationIdGenerator;

/** Test-only generator reset by the integration-test data cleaner. */
public final class DeterministicTestNotificationIdGenerator implements NotificationIdGenerator {

    private long sequence;

    @Override
    public synchronized String nextId() {
        return "notification-" + String.format("%020d", ++sequence);
    }

    public synchronized void reset() {
        sequence = 0L;
    }
}
