package com.carwash.shared.application;

import java.util.function.Supplier;

/** Bounded, persistence-neutral instrumentation. Never pass customer data to this port. */
public interface Telemetry {
    enum Operation {
        BOOKING_CREATE, BOOKING_UPDATE, BOOKING_RESCHEDULE, BOOKING_CANCEL, BOOKING_CONFIRM, BOOKING_DELETE,
        QUEUE_CREATE, QUEUE_MOVE, QUEUE_CALL, QUEUE_START, QUEUE_COMPLETE, QUEUE_DELETE,
        AVAILABILITY_SEARCH, AVAILABILITY_DECISION, RECOMMENDATION, NOTIFICATION_CREATE, AUDIT_APPEND,
        PERSISTENCE_READ, PERSISTENCE_WRITE,
        FEEDBACK_SUBMIT, FEEDBACK_REPORT, FEEDBACK_MODERATE, REPORT_READ
    }

    <T> T observe(Operation operation, Supplier<T> action);

    default void observe(Operation operation, Runnable action) {
        observe(operation, () -> { action.run(); return null; });
    }

    default void candidates(int count) { }

    Telemetry NONE = new Telemetry() {
        @Override public <T> T observe(Operation operation, Supplier<T> action) { return action.get(); }
    };
}
