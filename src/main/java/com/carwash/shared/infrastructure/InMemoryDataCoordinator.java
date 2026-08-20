package com.carwash.shared.infrastructure;

import com.carwash.shared.application.DataTransactionOperations;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Coordinates repository and aggregate mutations for the current single-JVM,
 * in-memory runtime.
 *
 * <p>This is not a distributed lock or a database transaction. The explicit
 * PostgreSQL profile supplies those guarantees through another implementation
 * of the shared application port.</p>
 */
public final class InMemoryDataCoordinator implements DataTransactionOperations {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    @Override
    public <T> T read(Supplier<T> operation) {
        Objects.requireNonNull(operation, "Operation is required");
        lock.readLock().lock();
        try {
            return operation.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void read(Runnable operation) {
        Objects.requireNonNull(operation, "Operation is required");
        read(() -> {
            operation.run();
            return null;
        });
    }

    @Override
    public <T> T write(Supplier<T> operation) {
        Objects.requireNonNull(operation, "Operation is required");
        lock.writeLock().lock();
        try {
            return operation.get();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void write(Runnable operation) {
        Objects.requireNonNull(operation, "Operation is required");
        write(() -> {
            operation.run();
            return null;
        });
    }

    @Override
    public void compensate(RuntimeException failure, Runnable compensation) {
        try {
            compensation.run();
        } catch (RuntimeException compensationFailure) {
            failure.addSuppressed(compensationFailure);
        }
    }

    @Override
    public void afterCommitBestEffort(
            Runnable action,
            java.util.function.Consumer<RuntimeException> failureHandler
    ) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            failureHandler.accept(failure);
        }
    }
}
