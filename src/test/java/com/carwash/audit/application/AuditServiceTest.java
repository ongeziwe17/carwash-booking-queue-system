package com.carwash.audit.application;

import com.carwash.audit.domain.*;
import com.carwash.audit.infrastructure.InMemoryAuditRepository;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class AuditServiceTest {
    private static final Instant NOW = Instant.parse("2089-01-15T12:00:00.123456789Z");
    private final InMemoryDataCoordinator transactions = new InMemoryDataCoordinator();
    private final AtomicInteger sequence = new AtomicInteger();
    private final AuditProperties properties = properties();
    private final InMemoryAuditRepository repository = new InMemoryAuditRepository(
            transactions, new AuditMetadataPolicy(properties));
    private final AuditService service = new AuditService(repository, transactions,
            new AuditMetadataPolicy(properties), () -> "audit-" + sequence.incrementAndGet(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach void closeContext() { AuditRequestContext.close(); }

    @Test void successRecordAndProtectedMutationCommitExactlyOnce() {
        AtomicInteger mutations = new AtomicInteger();
        AuditRequestContext.open("correlation-1");

        String result = service.execute(command(AuditAction.BUSINESS_UPDATED), () -> {
            mutations.incrementAndGet();
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(mutations).hasValue(1);
        List<AuditRecord> records = all();
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.action()).isEqualTo(AuditAction.BUSINESS_UPDATED);
            assertThat(record.outcome()).isEqualTo(AuditOutcome.SUCCESS);
            assertThat(record.correlationId()).isEqualTo("correlation-1");
            assertThat(record.occurredAt()).isEqualTo(NOW);
        });
    }

    @Test void failureEventSurvivesRollbackAndSuccessEventDoesNot() {
        AuditRequestContext.open("correlation-2");

        assertThatThrownBy(() -> service.execute(command(AuditAction.BRANCH_UPDATED), () -> {
            throw new AccessDeniedException("raw details must not be stored");
        })).isInstanceOf(AccessDeniedException.class);

        assertThat(all()).singleElement().satisfies(record -> {
            assertThat(record.outcome()).isEqualTo(AuditOutcome.DENIED);
            assertThat(record.reasonCode()).isEqualTo("ACCESS_DENIED");
            assertThat(record.metadata().toString()).doesNotContain("raw details");
        });
    }

    @Test void mandatoryAppendFailurePreventsProtectedMutation() {
        AtomicBoolean mutated = new AtomicBoolean();
        AuditRepository failing = new AuditRepository() {
            public void append(AuditRecord record) { throw new IllegalStateException("unavailable"); }
            public List<AuditRecord> queryByBusinessId(String id, AuditQuery query) { return List.of(); }
            public List<AuditRecord> queryPlatform(AuditQuery query) { return List.of(); }
        };
        AuditService unavailable = new AuditService(failing, transactions, new AuditMetadataPolicy(properties),
                () -> "audit-failure", Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> unavailable.execute(command(AuditAction.BOOKING_RESCHEDULED), () -> {
            mutated.set(true);
            return null;
        })).isInstanceOf(IllegalStateException.class);
        assertThat(mutated).isFalse();
    }

    @Test void duplicateBoundaryRecordIsSuppressedWithinOneRequest() {
        AuditRequestContext.open("correlation-3");
        service.appendIsolated(command(AuditAction.AUTHORIZATION_DENIED), AuditOutcome.DENIED, "ACCESS_DENIED");
        service.appendIsolated(command(AuditAction.AUTHORIZATION_DENIED), AuditOutcome.DENIED, "ACCESS_DENIED");
        assertThat(all()).hasSize(1);
    }

    @Test void metadataIsAllowlistedBoundedAndDeterministicallyOrdered() {
        AuditMetadataPolicy policy = new AuditMetadataPolicy(properties);
        assertThat(policy.sanitize(Map.of("scope", "TENANT", "category", "API")))
                .containsExactly(entry("category", "API"), entry("scope", "TENANT"));
        for (String key : List.of("password", "token", "authorization", "cookie", "secret", "credential",
                "email", "phone", "address", "message")) {
            assertThatThrownBy(() -> policy.sanitize(Map.of(key, "unsafe")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> policy.sanitize(Map.of("scope", "x".repeat(257))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.sanitize(Map.of("category", "Bearer secret-token")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.sanitize(Map.of("operation",
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ2aWN0aW0ifQ.invalidSignature")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> transactions.write(() -> repository.append(new AuditRecord(
                "unsafe-direct", NOW, AuditActorType.SYSTEM, null, null, null,
                AuditAction.PLATFORM_ADMIN_OPERATION, "TEST_RESOURCE", null, AuditOutcome.SUCCESS,
                null, "unsafe-direct", Map.of("password", "must-never-persist"), AuditSource.SYSTEM))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(all()).isEmpty();
    }

    @Test void configurationRejectsUnsafeBounds() {
        assertThatThrownBy(() -> new AuditProperties(Duration.ofDays(1), 20, 10,
                Duration.ofDays(1), 2, 8, 16, 128)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditProperties(Duration.ZERO, 10, 10,
                Duration.ofDays(1), 2, 8, 16, 128)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void repositoryContractCannotUpdateOrDeleteHistory() {
        assertThat(AuditRepository.class.getMethods()).extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("append", "queryByBusinessId", "queryPlatform");
    }

    @Test void inMemoryConcurrentAppendsNeverOverwriteEvents() throws Exception {
        int count = 24;
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(6)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var futures = java.util.stream.IntStream.range(0, count).mapToObj(index -> executor.submit(() -> {
                start.await();
                AuditRequestContext.open("concurrent-" + index);
                try {
                    service.appendIsolated(command(AuditAction.QUEUE_CALLED), AuditOutcome.SUCCESS, null);
                } finally {
                    AuditRequestContext.close();
                }
                return null;
            })).toList();
            start.countDown();
            for (var future : futures) future.get();
        }
        assertThat(all()).hasSize(count).extracting(AuditRecord::auditId).doesNotHaveDuplicates();
    }

    private AuditCommand command(AuditAction action) {
        return new AuditCommand(action, action, AuditActor.user("owner-a", "BUSINESS_OWNER", "business-a"),
                "business-a", "TEST_RESOURCE", "target-a", AuditSource.API,
                Map.of("scope", "TENANT"));
    }

    private List<AuditRecord> all() {
        return repository.queryPlatform(new AuditQuery(null, null, null, null, null,
                NOW.minusSeconds(1), NOW.plusSeconds(1), null, 1_000));
    }

    static AuditProperties properties() {
        return new AuditProperties(Duration.ofDays(365), 50, 200, Duration.ofDays(90), 10, 32, 256, 2048);
    }
}
