package com.carwash.audit.application;

import com.carwash.audit.domain.AuditOutcome;

import java.util.function.Supplier;

/** Narrow scalar contract used by other capabilities. */
public interface AuditOperations {
    <T> T execute(AuditCommand command, Supplier<T> protectedMutation);
    <T> T execute(AuditCommand successCommand, AuditCommand failureCommand, Supplier<T> protectedMutation);
    void appendIsolated(AuditCommand command, AuditOutcome outcome, String reasonCode);

    default void execute(AuditCommand command, Runnable protectedMutation) {
        execute(command, () -> { protectedMutation.run(); return null; });
    }

    static AuditOperations noOp() {
        return new AuditOperations() {
            public <T> T execute(AuditCommand command, Supplier<T> mutation) { return mutation.get(); }
            public <T> T execute(AuditCommand success, AuditCommand failure, Supplier<T> mutation) { return mutation.get(); }
            public void appendIsolated(AuditCommand command, AuditOutcome outcome, String reasonCode) { }
        };
    }
}
