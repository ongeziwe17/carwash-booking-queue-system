package com.carwash.audit.application;

import com.carwash.audit.domain.*;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import com.carwash.audit.infrastructure.InMemoryAuditRepository;

import static org.assertj.core.api.Assertions.*;

class AuditQueryServiceTest {
    private static final Instant NOW = Instant.parse("2089-01-15T12:00:00Z");

    @Test void ownerQueryUsesOnlyAuthenticatedTenantPredicate() {
        TrackingRepository repository = new TrackingRepository();
        AuditQueryService service = service(repository);

        service.query(new AuditPrincipal("owner", "BUSINESS_OWNER", "business-a"), request(null, null));

        assertThat(repository.tenantScope).isEqualTo("business-a");
        assertThat(repository.platformQueries).isZero();
    }

    @Test void ownerCannotSupplyTenantOrPlatformScopeOverride() {
        AuditQueryService service = service(new TrackingRepository());
        AuditPrincipal owner = new AuditPrincipal("owner", "BUSINESS_OWNER", "business-a");
        assertThatThrownBy(() -> service.query(owner, request(AuditScope.TENANT, "business-b")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.query(owner, request(AuditScope.PLATFORM, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void staffAndCustomerAreDeniedEvenAtApplicationBoundary() {
        AuditQueryService service = service(new TrackingRepository());
        for (String role : List.of("STAFF", "CUSTOMER")) {
            assertThatThrownBy(() -> service.query(new AuditPrincipal("user", role,
                            role.equals("STAFF") ? "business-a" : null), request(null, null)))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test void platformAdministratorMustChooseExplicitTenantOrPlatformScope() {
        TrackingRepository repository = new TrackingRepository();
        AuditQueryService service = service(repository);
        AuditPrincipal admin = new AuditPrincipal("admin", "PLATFORM_ADMIN", null);

        assertThatThrownBy(() -> service.query(admin, request(null, null)))
                .isInstanceOf(AccessDeniedException.class);
        service.query(admin, request(AuditScope.TENANT, "business-b"));
        assertThat(repository.tenantScope).isEqualTo("business-b");
        service.query(admin, request(AuditScope.PLATFORM, null));
        assertThat(repository.platformQueries).isEqualTo(1);
    }

    @Test void queryRangeLimitAndCursorAreBounded() {
        AuditQueryService service = service(new TrackingRepository());
        AuditPrincipal owner = new AuditPrincipal("owner", "BUSINESS_OWNER", "business-a");
        AuditQueryRequest tooWide = new AuditQueryRequest(null, null, null, null, null,
                NOW.minus(java.time.Duration.ofDays(91)), NOW, null, null, null, null);
        assertThatThrownBy(() -> service.query(owner, tooWide)).isInstanceOf(RuntimeException.class);
        AuditQueryRequest badCursor = new AuditQueryRequest(null, null, null, null, null,
                null, null, "not-a-cursor", null, null, null);
        assertThatThrownBy(() -> service.query(owner, badCursor)).isInstanceOf(RuntimeException.class);
        AuditQueryRequest oversized = new AuditQueryRequest(null, null, null, null, null,
                null, null, null, 201, null, null);
        assertThatThrownBy(() -> service.query(owner, oversized)).isInstanceOf(RuntimeException.class);
    }

    @Test void exactTimestampAndAuditIdProvideDeterministicIncrementalCursorOrdering() {
        InMemoryDataCoordinator transactions = new InMemoryDataCoordinator();
        InMemoryAuditRepository repository = new InMemoryAuditRepository(
                transactions, new AuditMetadataPolicy(AuditServiceTest.properties()));
        transactions.write(() -> {
            repository.append(record("audit-a"));
            repository.append(record("audit-c"));
            repository.append(record("audit-b"));
        });
        AuditQueryService service = new AuditQueryService(repository, transactions, AuditOperations.noOp(),
                AuditServiceTest.properties(), Clock.fixed(NOW, ZoneOffset.UTC));
        AuditPrincipal owner = new AuditPrincipal("owner", "BUSINESS_OWNER", "business-a");
        AuditQueryRequest firstRequest = new AuditQueryRequest(null, null, null, null, null,
                NOW.minusSeconds(1), NOW.plusSeconds(1), null, 2, null, null);

        AuditPage first = service.query(owner, firstRequest);
        assertThat(first.records()).extracting(AuditRecord::auditId).containsExactly("audit-c", "audit-b");
        assertThat(first.nextCursor()).isNotBlank();
        AuditPage second = service.query(owner, new AuditQueryRequest(null, null, null, null, null,
                NOW.minusSeconds(1), NOW.plusSeconds(1), first.nextCursor(), 2, null, null));
        assertThat(second.records()).extracting(AuditRecord::auditId).containsExactly("audit-a");
        assertThat(second.nextCursor()).isNull();
    }

    private AuditQueryService service(AuditRepository repository) {
        return new AuditQueryService(repository, new InMemoryDataCoordinator(), AuditOperations.noOp(),
                AuditServiceTest.properties(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AuditQueryRequest request(AuditScope scope, String businessId) {
        return new AuditQueryRequest(null, null, null, null, null,
                null, null, null, null, scope, businessId);
    }

    private AuditRecord record(String id) {
        return new AuditRecord(id, NOW, AuditActorType.SYSTEM, null, null, "business-a",
                AuditAction.BRANCH_UPDATED, "BRANCH", id, AuditOutcome.SUCCESS, null,
                "correlation-" + id, Map.of(), AuditSource.SYSTEM);
    }

    private static final class TrackingRepository implements AuditRepository {
        private String tenantScope;
        private int platformQueries;
        public void append(AuditRecord record) { }
        public List<AuditRecord> queryByBusinessId(String businessId, AuditQuery query) {
            tenantScope = businessId;
            return List.of();
        }
        public List<AuditRecord> queryPlatform(AuditQuery query) {
            platformQueries++;
            return List.of();
        }
    }
}
