package com.carwash.shared.infrastructure;

import com.carwash.shared.application.DataTransactionOperations;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Component
@Profile("postgres")
public final class PostgresTransactionOperations implements DataTransactionOperations {

    private final TransactionTemplate readOnly;
    private final TransactionTemplate required;
    private final TransactionTemplate requiresNew;
    private final com.carwash.shared.application.Telemetry telemetry;

    public PostgresTransactionOperations(PlatformTransactionManager transactionManager) {
        this(transactionManager, com.carwash.shared.application.Telemetry.NONE);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PostgresTransactionOperations(PlatformTransactionManager transactionManager,
                                        com.carwash.shared.application.Telemetry telemetry) {
        this.telemetry = telemetry;
        this.readOnly = template(transactionManager, TransactionDefinition.PROPAGATION_REQUIRED, true);
        this.required = template(transactionManager, TransactionDefinition.PROPAGATION_REQUIRED, false);
        this.requiresNew = template(transactionManager, TransactionDefinition.PROPAGATION_REQUIRES_NEW, false);
    }

    @Override
    public <T> T read(Supplier<T> action) {
        return telemetry.observe(com.carwash.shared.application.Telemetry.Operation.PERSISTENCE_READ,
                () -> readOnly.execute(status -> action.get()));
    }

    @Override
    public <T> T write(Supplier<T> action) {
        return telemetry.observe(com.carwash.shared.application.Telemetry.Operation.PERSISTENCE_WRITE,
                () -> required.execute(status -> action.get()));
    }

    @Override
    public <T> T isolatedWrite(Supplier<T> action) {
        return telemetry.observe(com.carwash.shared.application.Telemetry.Operation.PERSISTENCE_WRITE,
                () -> requiresNew.execute(status -> action.get()));
    }

    @Override
    public void onRollback(Runnable action) {
        Objects.requireNonNull(action, "Rollback action is required");
        // PostgreSQL rollback is authoritative; no application compensation is registered.
    }

    @Override
    public void compensate(RuntimeException failure, Runnable compensation) {
        // Database rollback is authoritative.
    }

    @Override
    public void afterCommitBestEffort(Runnable action, Consumer<RuntimeException> failureHandler) {
        Objects.requireNonNull(action, "After-commit action is required");
        Objects.requireNonNull(failureHandler, "Failure handler is required");
        Runnable isolated = () -> {
            try {
                requiresNew.executeWithoutResult(status -> action.run());
            } catch (RuntimeException failure) {
                failureHandler.accept(failure);
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    isolated.run();
                }
            });
        } else {
            isolated.run();
        }
    }

    private static TransactionTemplate template(
            PlatformTransactionManager manager,
            int propagation,
            boolean readOnly
    ) {
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setPropagationBehavior(propagation);
        template.setReadOnly(readOnly);
        return template;
    }

    @Override
    public void afterCompletion(Consumer<Boolean> callback) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    safelyComplete(callback, status == STATUS_COMMITTED);
                }
            });
        } else {
            safelyComplete(callback, true);
        }
    }

    private static void safelyComplete(Consumer<Boolean> callback, boolean committed) {
        try { callback.accept(committed); } catch (RuntimeException ignored) { /* telemetry is best effort */ }
    }
}
