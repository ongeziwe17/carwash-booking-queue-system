package com.carwash.shared.infrastructure;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Coordinates repository and aggregate mutations for the current single-JVM,
 * in-memory runtime.
 *
 * <p>This is not a distributed lock or a database transaction. DATA-002 must
 * replace this boundary with database transactions and suitable locking when
 * persistent storage or multiple application instances are introduced.</p>
 */
public final class InMemoryDataCoordinator {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

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
}
