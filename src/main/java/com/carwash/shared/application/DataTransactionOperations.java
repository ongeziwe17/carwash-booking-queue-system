package com.carwash.shared.application;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Persistence-neutral unit-of-work boundary used by application services. */
public interface DataTransactionOperations {

    <T> T read(Supplier<T> action);

    <T> T write(Supplier<T> action);

    /** Runs a write in an isolated transaction after the caller's transaction has completed. */
    default <T> T isolatedWrite(Supplier<T> action) { return write(action); }

    default void read(Runnable action) {
        read(() -> {
            action.run();
            return null;
        });
    }

    default void write(Runnable action) {
        write(() -> {
            action.run();
            return null;
        });
    }

    default void isolatedWrite(Runnable action) {
        isolatedWrite(() -> {
            action.run();
            return null;
        });
    }

    /** Registers in-memory rollback work; database implementations rely on the transaction manager. */
    default void onRollback(Runnable action) { }

    void compensate(RuntimeException failure, Runnable compensation);

    void afterCommitBestEffort(Runnable action, Consumer<RuntimeException> failureHandler);
}
