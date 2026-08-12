package com.carwash.marketplace.infrastructure;

import com.carwash.marketplace.domain.BranchOperatingSchedule;
import com.carwash.marketplace.domain.ClosureStatus;
import com.carwash.marketplace.domain.TemporaryBranchClosure;
import com.carwash.marketplace.domain.WeeklyOperatingInterval;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryBranchSchedulingRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 8, 0);

    @Test
    void scheduleRepositoryPreservesExplicitInsertAndUpdateSemantics() {
        InMemoryBranchOperatingScheduleRepository repository = new InMemoryBranchOperatingScheduleRepository();
        BranchOperatingSchedule schedule = schedule("branch-001", List.of(
                new WeeklyOperatingInterval(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))));

        assertTrue(repository.insert(schedule));
        assertFalse(repository.insert(schedule));
        assertTrue(repository.update(schedule.replace(List.of(), NOW.plusHours(1))));
        assertTrue(repository.findById("branch-001").orElseThrow().getIntervals().isEmpty());
        assertFalse(repository.update(schedule("branch-missing", List.of())));
    }

    @Test
    void scheduleRepositoryReturnsDetachedImmutableIntervalState() {
        InMemoryBranchOperatingScheduleRepository repository = new InMemoryBranchOperatingScheduleRepository();
        List<WeeklyOperatingInterval> mutable = new ArrayList<>();
        mutable.add(new WeeklyOperatingInterval(
                DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0)));
        repository.insert(schedule("branch-001", mutable));
        mutable.clear();

        BranchOperatingSchedule stored = repository.findById("branch-001").orElseThrow();
        assertEquals(1, stored.getIntervals().size());
        assertThrows(UnsupportedOperationException.class, () -> stored.getIntervals().clear());
        assertThrows(UnsupportedOperationException.class, () -> repository.findAll().clear());
    }

    @Test
    void closureRepositoryUsesGlobalIdsAndDeterministicBranchScopedRetrieval() {
        InMemoryTemporaryBranchClosureRepository repository = new InMemoryTemporaryBranchClosureRepository();
        TemporaryBranchClosure second = closure("closure-b", "branch-001");
        TemporaryBranchClosure first = closure("closure-a", "branch-001");
        TemporaryBranchClosure other = closure("closure-c", "branch-002");

        assertTrue(repository.insert(second));
        assertTrue(repository.insert(first));
        assertTrue(repository.insert(other));
        assertFalse(repository.insert(first));
        assertEquals(List.of("closure-a", "closure-b"), repository.findByBranchId("branch-001")
                .stream().map(TemporaryBranchClosure::getClosureId).toList());
        assertTrue(repository.findByBranchId("branch-missing").isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> repository.findByBranchId("branch-001").clear());
    }

    @Test
    void closureRepositoryUpdatesExistingButNeverUpsertsMissingRecords() {
        InMemoryTemporaryBranchClosureRepository repository = new InMemoryTemporaryBranchClosureRepository();
        TemporaryBranchClosure closure = closure("closure-a", "branch-001");
        repository.insert(closure);

        assertTrue(repository.update(closure.cancel(NOW.plusHours(1))));
        assertEquals(ClosureStatus.CANCELLED,
                repository.findById("closure-a").orElseThrow().getStatus());
        assertFalse(repository.update(closure("closure-missing", "branch-001")));
    }

    private BranchOperatingSchedule schedule(String branchId, List<WeeklyOperatingInterval> intervals) {
        return new BranchOperatingSchedule(branchId, intervals, NOW, NOW);
    }

    private TemporaryBranchClosure closure(String closureId, String branchId) {
        return new TemporaryBranchClosure(
                closureId,
                branchId,
                Instant.parse("2030-01-07T08:00:00Z"),
                Instant.parse("2030-01-07T10:00:00Z"),
                "Maintenance",
                ClosureStatus.ACTIVE,
                NOW,
                NOW
        );
    }
}
