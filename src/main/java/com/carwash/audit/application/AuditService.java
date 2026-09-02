package com.carwash.audit.application;

import com.carwash.audit.domain.*;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Authoritative append service. Mandatory success records participate in the protected transaction. */
public final class AuditService implements AuditOperations {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditService.class);
    private final AuditRepository repository;
    private final DataTransactionOperations transactions;
    private final AuditMetadataPolicy metadataPolicy;
    private final AuditIdGenerator ids;
    private final Clock clock;

    public AuditService(AuditRepository repository, DataTransactionOperations transactions,
                        AuditMetadataPolicy metadataPolicy, AuditIdGenerator ids, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.transactions = Objects.requireNonNull(transactions);
        this.metadataPolicy = Objects.requireNonNull(metadataPolicy);
        this.ids = Objects.requireNonNull(ids);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public <T> T execute(AuditCommand command, Supplier<T> protectedMutation) {
        return execute(command, command, protectedMutation);
    }

    @Override
    public <T> T execute(AuditCommand successCommand, AuditCommand failureCommand, Supplier<T> protectedMutation) {
        Prepared success = prepare(successCommand);
        try {
            return transactions.write(() -> {
                // Append first: a mandatory append failure cannot leave an in-memory mutation behind.
                AuditRecord record = append(success, AuditOutcome.SUCCESS, null, successCommand.successAction());
                T result = protectedMutation.get();
                logAfterCommit(record);
                return result;
            });
        } catch (RuntimeException original) {
            appendFailureWithoutMasking(prepare(failureCommand), original);
            throw original;
        }
    }

    @Override
    public void appendIsolated(AuditCommand command, AuditOutcome outcome, String reasonCode) {
        Prepared prepared = prepare(command);
        transactions.isolatedWrite(() -> {
            AuditRecord record = append(prepared, outcome, safeCode(reasonCode),
                    outcome == AuditOutcome.SUCCESS ? command.successAction() : command.failureAction());
            logAfterCommit(record);
        });
    }

    private void appendFailureWithoutMasking(Prepared prepared, RuntimeException original) {
        try {
            AuditOutcome outcome = original instanceof AccessDeniedException
                    || original instanceof ResourceNotFoundException ? AuditOutcome.DENIED : AuditOutcome.FAILURE;
            String code = original instanceof ResourceNotFoundException ? "RESOURCE_NOT_FOUND_OR_FOREIGN"
                    : original instanceof AccessDeniedException ? "ACCESS_DENIED" : "OPERATION_FAILED";
            transactions.isolatedWrite(() -> {
                AuditRecord record = append(prepared, outcome, code, prepared.command().failureAction());
                logAfterCommit(record);
            });
        } catch (RuntimeException auditFailure) {
            LOGGER.error("audit_persistence_failure action={} outcome={} correlationId={}",
                    prepared.command().failureAction(), "FAILURE", prepared.correlationId());
        }
    }

    private AuditRecord append(Prepared prepared, AuditOutcome outcome, String reasonCode, AuditAction action) {
        if (!AuditRequestContext.markOnce(action + ":" + outcome + ":" + prepared.command().targetId())) return null;
        AuditActor actor = prepared.command().actor();
        AuditRecord record = new AuditRecord(ids.nextId(), Instant.now(clock), actor.type(), actor.userId(),
                actor.role(), prepared.command().businessId(), action, prepared.command().targetType(),
                prepared.command().targetId(), outcome, safeCode(reasonCode), prepared.correlationId(),
                prepared.metadata(), prepared.command().source());
        repository.append(record);
        return record;
    }

    private void logAfterCommit(AuditRecord record) {
        if (record == null) return;
        transactions.afterCommitBestEffort(() -> LOGGER.info(
                "audit_event auditId={} action={} outcome={} actorType={} actorUserId={} businessId={} targetType={} targetId={} source={} correlationId={}",
                record.auditId(), record.action(), record.outcome(), record.actorType(), record.actorUserId(),
                record.businessId(), record.targetType(), record.targetId(), record.source(), record.correlationId()),
                failure -> LOGGER.error("audit_structured_log_failure auditId={} correlationId={}",
                        record.auditId(), record.correlationId()));
    }

    private Prepared prepare(AuditCommand command) {
        Objects.requireNonNull(command, "Audit command is required");
        return new Prepared(command, metadataPolicy.sanitize(command.metadata()), AuditRequestContext.correlationId());
    }

    private static String safeCode(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.matches("[A-Z0-9_]{1,64}") ? normalized : "OPERATION_FAILED";
    }

    private record Prepared(AuditCommand command, Map<String, String> metadata, String correlationId) { }
}
