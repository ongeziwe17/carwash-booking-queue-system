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

    public PostgresTransactionOperations(PlatformTransactionManager transactionManager) {
        this.readOnly = template(transactionManager, TransactionDefinition.PROPAGATION_REQUIRED, true);
        this.required = template(transactionManager, TransactionDefinition.PROPAGATION_REQUIRED, false);
        this.requiresNew = template(transactionManager, TransactionDefinition.PROPAGATION_REQUIRES_NEW, false);
    }

    @Override
    public <T> T read(Supplier<T> action) {
        return readOnly.execute(status -> action.get());
    }

    @Override
    public <T> T write(Supplier<T> action) {
        return required.execute(status -> action.get());
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
}
