package com.carwash.shared.infrastructure;

import com.carwash.shared.application.DataTransactionOperations;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.ArrayDeque;
import java.util.Deque;
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

    private final com.carwash.shared.application.Telemetry telemetry;
    public InMemoryDataCoordinator() { this(com.carwash.shared.application.Telemetry.NONE); }
    public InMemoryDataCoordinator(com.carwash.shared.application.Telemetry telemetry) { this.telemetry = telemetry; }

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final ThreadLocal<Deque<Runnable>> rollbackActions = new ThreadLocal<>();
    private final ThreadLocal<java.util.List<java.util.function.Consumer<Boolean>>> completion = new ThreadLocal<>();

    @Override
    public <T> T read(Supplier<T> operation) {
        Objects.requireNonNull(operation, "Operation is required");
        lock.readLock().lock();
        try {
            return telemetry.observe(com.carwash.shared.application.Telemetry.Operation.PERSISTENCE_READ, operation);
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
        boolean outermost = rollbackActions.get() == null;
        if (outermost) {
            rollbackActions.set(new ArrayDeque<>());
            completion.set(new java.util.ArrayList<>());
        }
        boolean committed = false;
        try {
            T result = telemetry.observe(com.carwash.shared.application.Telemetry.Operation.PERSISTENCE_WRITE, operation);
            committed = true;
            return result;
        } catch (RuntimeException failure) {
            if (outermost) {
                Deque<Runnable> actions = rollbackActions.get();
                while (!actions.isEmpty()) compensate(failure, actions.pop());
            }
            throw failure;
        } finally {
            if (outermost) {
                var callbacks = completion.get();
                rollbackActions.remove();
                completion.remove();
                for (var callback : callbacks) safelyComplete(callback, committed);
            }
            lock.writeLock().unlock();
        }
    }

    @Override
    public <T> T isolatedWrite(Supplier<T> operation) {
        // The shared write lock is the in-memory equivalent of an isolated write.
        return write(operation);
    }

    @Override
    public void onRollback(Runnable action) {
        Objects.requireNonNull(action, "Rollback action is required");
        Deque<Runnable> actions = rollbackActions.get();
        if (actions == null) throw new IllegalStateException("Rollback action requires an active write boundary");
        actions.push(action);
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

    @Override
    public void afterCompletion(java.util.function.Consumer<Boolean> callback) {
        var callbacks = completion.get();
        if (callbacks == null) safelyComplete(callback, true);
        else callbacks.add(callback);
    }

    private static void safelyComplete(java.util.function.Consumer<Boolean> callback, boolean committed) {
        try { callback.accept(committed); } catch (RuntimeException ignored) { /* telemetry is best effort */ }
    }
}
