package com.carwash.audit.application;

import com.carwash.audit.domain.*;
import com.carwash.shared.application.DataTransactionOperations;
import org.springframework.security.access.AccessDeniedException;
import com.carwash.shared.exception.BusinessRuleViolationException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

public final class AuditQueryService {
    private final AuditRepository repository;
    private final DataTransactionOperations transactions;
    private final AuditOperations audit;
    private final AuditProperties properties;
    private final Clock clock;

    public AuditQueryService(AuditRepository repository, DataTransactionOperations transactions,
                             AuditOperations audit, AuditProperties properties, Clock clock) {
        this.repository = repository;
        this.transactions = transactions;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    public AuditPage query(AuditPrincipal principal, AuditQueryRequest request) {
        Objects.requireNonNull(principal, "Authenticated principal is required");
        Objects.requireNonNull(request, "Audit query is required");
        Scope scope = authorize(principal, request);
        AuditQuery query = normalize(request);
        AuditCommand event = AuditCommand.actionForBusiness(AuditAction.AUDIT_RECORDS_READ,
                AuditActor.user(principal.userId(), principal.role(), principal.businessId()),
                scope.platform() ? null : scope.businessId(), "AUDIT_RECORD", null, AuditSource.API);
        return audit.execute(event, () -> transactions.read(() -> page(
                scope.platform() ? repository.queryPlatform(query) : repository.queryByBusinessId(scope.businessId(), query),
                query.limit() - 1)));
    }

    private AuditPage page(List<AuditRecord> selected, int requestedLimit) {
        boolean more = selected.size() > requestedLimit;
        List<AuditRecord> records = more ? List.copyOf(selected.subList(0, requestedLimit)) : List.copyOf(selected);
        String cursor = more && !records.isEmpty() ? encode(records.get(records.size() - 1)) : null;
        return new AuditPage(records, cursor);
    }

    private Scope authorize(AuditPrincipal principal, AuditQueryRequest request) {
        if ("BUSINESS_OWNER".equals(principal.role())) {
            if (principal.businessId() == null || request.businessId() != null || request.scope() != null) {
                throw new AccessDeniedException("Tenant audit scope is server controlled");
            }
            return new Scope(false, principal.businessId());
        }
        if (!"PLATFORM_ADMIN".equals(principal.role())) throw new AccessDeniedException("Audit access is denied");
        if (request.scope() == null) throw new AccessDeniedException("Platform audit scope must be explicit");
        if (request.scope() == AuditScope.PLATFORM) {
            if (request.businessId() != null) throw new AccessDeniedException("Platform scope cannot include a tenant");
            return new Scope(true, null);
        }
        if (request.businessId() == null || request.businessId().isBlank()) {
            throw new AccessDeniedException("Tenant scope requires a business identifier");
        }
        return new Scope(false, normalize(request.businessId()));
    }

    private AuditQuery normalize(AuditQueryRequest request) {
        Instant now = Instant.now(clock);
        Instant to = request.to() == null ? now : request.to();
        Instant from = request.from() == null ? to.minus(properties.maximumQueryRange()) : request.from();
        if (from.isAfter(to) || java.time.Duration.between(from, to).compareTo(properties.maximumQueryRange()) > 0) {
            throw new BusinessRuleViolationException("Audit date range is invalid or too large");
        }
        int requestedLimit = request.limit() == null ? properties.defaultPageSize() : request.limit();
        if (requestedLimit < 1 || requestedLimit > properties.maximumPageSize()) {
            throw new BusinessRuleViolationException("Audit page limit is outside the configured bounds");
        }
        return new AuditQuery(normalize(request.actorUserId()), request.action(), request.outcome(),
                normalize(request.targetType()), normalize(request.targetId()), from, to,
                decode(request.cursor()), requestedLimit + 1);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new BusinessRuleViolationException("Audit filter is invalid");
        }
        return normalized;
    }

    private static String encode(AuditRecord record) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (record.occurredAt() + "|" + record.auditId()).getBytes(StandardCharsets.UTF_8));
    }

    private static AuditCursor decode(String cursor) {
        if (cursor == null) return null;
        if (cursor.isBlank() || cursor.length() > 512) {
            throw new BusinessRuleViolationException("Audit cursor is invalid");
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = decoded.indexOf('|');
            if (separator < 1) throw new IllegalArgumentException();
            String auditId = decoded.substring(separator + 1);
            if (auditId.isBlank() || auditId.length() > 64) throw new IllegalArgumentException();
            return new AuditCursor(Instant.parse(decoded.substring(0, separator)), auditId);
        } catch (RuntimeException invalid) {
            throw new BusinessRuleViolationException("Audit cursor is invalid");
        }
    }

    private record Scope(boolean platform, String businessId) { }
}
