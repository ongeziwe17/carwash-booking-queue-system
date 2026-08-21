package com.carwash.shared.application;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Persistence-neutral unit-of-work boundary used by application services. */
public interface DataTransactionOperations {

    <T> T read(Supplier<T> action);

    <T> T write(Supplier<T> action);

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

    void compensate(RuntimeException failure, Runnable compensation);

    void afterCommitBestEffort(Runnable action, Consumer<RuntimeException> failureHandler);
}
