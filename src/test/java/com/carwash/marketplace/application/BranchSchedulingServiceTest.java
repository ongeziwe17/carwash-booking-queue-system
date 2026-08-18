package com.carwash.marketplace.application;

import com.carwash.marketplace.domain.BranchOperatingScheduleRepository;
import com.carwash.marketplace.domain.ClosureStatus;
import com.carwash.marketplace.domain.TemporaryBranchClosureRepository;
import com.carwash.marketplace.infrastructure.InMemoryBranchOperatingScheduleRepository;
import com.carwash.marketplace.infrastructure.InMemoryCarWashBranchRepository;
import com.carwash.marketplace.infrastructure.InMemoryCarWashBusinessRepository;
import com.carwash.marketplace.infrastructure.InMemoryTemporaryBranchClosureRepository;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchSchedulingServiceTest {

    private InMemoryCarWashBusinessRepository businesses;
    private InMemoryCarWashBranchRepository branches;
    private BranchOperatingScheduleRepository schedules;
    private TemporaryBranchClosureRepository closures;
    private MarketplaceManagementService marketplace;
    private BranchSchedulingService scheduling;

    @BeforeEach
    void setUp() {
        businesses = new InMemoryCarWashBusinessRepository();
        branches = new InMemoryCarWashBranchRepository();
        schedules = new InMemoryBranchOperatingScheduleRepository();
        closures = new InMemoryTemporaryBranchClosureRepository();
        InMemoryDataCoordinator coordinator = new InMemoryDataCoordinator();
        Clock clock = Clock.fixed(Instant.parse("2030-01-01T06:00:00Z"), ZoneOffset.UTC);
        marketplace = new MarketplaceManagementService(businesses, branches, coordinator, clock);
        scheduling = new BranchSchedulingService(
                businesses, branches, schedules, closures, coordinator, clock);
        marketplace.registerBusiness(new RegisterBusinessCommand(
                "business-001", "Wash Group", "info@example.test", "+27 82 123 4567", "REG-001"));
    }

    @Test
    void branchWithNoScheduleAndExplicitlyEmptyScheduleAreClosed() {
        createBranch("branch-za", "Africa/Johannesburg", false);
        Instant mondayMorning = Instant.parse("2030-01-07T08:00:00Z");

        BranchOperatingScheduleSnapshot absent = scheduling.getOperatingSchedule("branch-za");
        assertTrue(absent.intervals().isEmpty());
        assertNull(absent.createdAt());
        assertFalse(scheduling.getOpenStatus("branch-za", mondayMorning).open());

        BranchOperatingScheduleSnapshot empty = scheduling.replaceOperatingSchedule(
                "branch-za", new ReplaceOperatingScheduleCommand(List.of()));
        assertTrue(empty.intervals().isEmpty());
        assertFalse(scheduling.getOpenStatus("branch-za", mondayMorning).withinWeeklyHours());
    }

    @Test
    void requestedInstantIsConvertedUsingEachCurrentBranchTimezone() {
        createBranch("branch-za", "Africa/Johannesburg", false);
        createBranch("branch-ny", "America/New_York", true);
        replaceWithMondayHours("branch-za");
        replaceWithMondayHours("branch-ny");

        BranchScheduleQuery query = scheduling;
        BranchOpenStatusSnapshot za = query.getOpenStatus(
                "branch-za", Instant.parse("2030-01-07T06:00:00Z"));
        BranchOpenStatusSnapshot ny = query.getOpenStatus(
                "branch-ny", Instant.parse("2030-01-07T13:00:00Z"));

        assertEquals(LocalTime.of(8, 0), za.branchLocalDateTime().toLocalTime());
        assertEquals("Africa/Johannesburg", za.timezone());
        assertTrue(za.open());
        assertEquals(LocalTime.of(8, 0), ny.branchLocalDateTime().toLocalTime());
        assertEquals("America/New_York", ny.timezone());
        assertTrue(ny.open());
        assertNull(za.applicableClosureId());
    }

    @Test
    void activeClosureOverridesHoursAtStartButNotAtExclusiveEndAndCancellationRestoresOpenState() {
        createBranch("branch-za", "Africa/Johannesburg", false);
        replaceWithMondayHours("branch-za");
        Instant start = Instant.parse("2030-01-07T08:00:00Z");
        Instant end = Instant.parse("2030-01-07T10:00:00Z");

        TemporaryBranchClosureSnapshot closure = scheduling.createTemporaryClosure(
                "branch-za", new CreateTemporaryBranchClosureCommand(
                        "closure-001", start, end, "Planned maintenance"));

        BranchOpenStatusSnapshot atStart = scheduling.getOpenStatus("branch-za", start);
        assertTrue(atStart.withinWeeklyHours());
        assertTrue(atStart.temporarilyClosed());
        assertFalse(atStart.open());
        assertEquals("closure-001", atStart.applicableClosureId());
        assertEquals("Planned maintenance", atStart.applicableClosureReason());
        assertTrue(scheduling.getOpenStatus("branch-za", end).open());

        TemporaryBranchClosureSnapshot cancelled = scheduling.cancelTemporaryClosure(closure.closureId());
        assertEquals(ClosureStatus.CANCELLED, cancelled.status());
        assertTrue(scheduling.getOpenStatus("branch-za", start).open());
        assertEquals(ClosureStatus.CANCELLED,
                scheduling.listTemporaryClosures("branch-za").getFirst().status());
    }

    @Test
    void completeServiceWindowMustStayInsideContinuousHoursAndAvoidAnyClosureOverlap() {
        createBranch("branch-window", "Africa/Johannesburg", true);
        replaceWithMondayHours("branch-window");
        Instant startsAt = Instant.parse("2030-01-07T08:00:00Z");

        assertTrue(scheduling.getServiceWindowStatus(
                "branch-window", startsAt, startsAt.plusSeconds(30 * 60)).open());
        assertFalse(scheduling.getServiceWindowStatus(
                "branch-window", Instant.parse("2030-01-07T14:45:00Z"),
                Instant.parse("2030-01-07T15:15:00Z")).withinWeeklyHours());

        scheduling.createTemporaryClosure("branch-window", new CreateTemporaryBranchClosureCommand(
                "closure-window", startsAt.plusSeconds(15 * 60), startsAt.plusSeconds(20 * 60), "Short closure"));
        BranchServiceWindowSnapshot closed = scheduling.getServiceWindowStatus(
                "branch-window", startsAt, startsAt.plusSeconds(30 * 60));
        assertTrue(closed.withinWeeklyHours());
        assertTrue(closed.temporarilyClosed());
        assertFalse(closed.open());
    }

    @Test
    void completeServiceWindowSupportsOvernightHoursAndAdjacentIntervals() {
        createBranch("branch-overnight", "Africa/Johannesburg", true);
        scheduling.replaceOperatingSchedule("branch-overnight", new ReplaceOperatingScheduleCommand(List.of(
                new WeeklyOperatingIntervalCommand(
                        DayOfWeek.MONDAY, LocalTime.of(20, 0), LocalTime.of(23, 0)),
                new WeeklyOperatingIntervalCommand(
                        DayOfWeek.MONDAY, LocalTime.of(23, 0), LocalTime.of(2, 0))
        )));

        assertTrue(scheduling.getServiceWindowStatus(
                "branch-overnight",
                Instant.parse("2030-01-07T19:30:00Z"),
                Instant.parse("2030-01-07T23:30:00Z")).open());
    }

    @Test
    void duplicateAndOverlappingActiveClosuresAreRejectedWhileAdjacencyAndCancelledReplacementAreAllowed() {
        createBranch("branch-za", "Africa/Johannesburg", true);
        Instant firstStart = Instant.parse("2030-01-07T08:00:00Z");
        Instant firstEnd = Instant.parse("2030-01-07T10:00:00Z");
        scheduling.createTemporaryClosure("branch-za", new CreateTemporaryBranchClosureCommand(
                "closure-001", firstStart, firstEnd, "First"));

        assertThrows(BusinessRuleViolationException.class,
                () -> scheduling.createTemporaryClosure("branch-za", new CreateTemporaryBranchClosureCommand(
                        "closure-001", firstEnd, firstEnd.plusSeconds(3600), "Duplicate ID")));
        assertThrows(BusinessRuleViolationException.class,
                () -> scheduling.createTemporaryClosure("branch-za", new CreateTemporaryBranchClosureCommand(
                        "closure-overlap", firstEnd.minusSeconds(1), firstEnd.plusSeconds(3600), "Overlap")));

        scheduling.createTemporaryClosure("branch-za", new CreateTemporaryBranchClosureCommand(
                "closure-adjacent", firstEnd, firstEnd.plusSeconds(3600), "Adjacent"));
        assertEquals(List.of("closure-001", "closure-adjacent"), scheduling.listTemporaryClosures("branch-za")
                .stream().map(TemporaryBranchClosureSnapshot::closureId).toList());

        scheduling.cancelTemporaryClosure("closure-001");
        scheduling.createTemporaryClosure("branch-za", new CreateTemporaryBranchClosureCommand(
                "closure-replacement", firstStart, firstEnd, "Replacement"));
        assertEquals(3, closures.findByBranchId("branch-za").size());
    }

    @Test
    void invalidClosureRangeAndReasonAreRejected() {
        createBranch("branch-za", "Africa/Johannesburg", true);
        Instant start = Instant.parse("2030-01-07T08:00:00Z");

        assertThrows(BusinessRuleViolationException.class,
                () -> scheduling.createTemporaryClosure("branch-za", new CreateTemporaryBranchClosureCommand(
                        "closure-equal", start, start, "Equal")));
        assertThrows(BusinessRuleViolationException.class,
                () -> scheduling.createTemporaryClosure("branch-za", new CreateTemporaryBranchClosureCommand(
                        "closure-reverse", start, start.minusSeconds(1), "Reverse")));
        assertThrows(BusinessRuleViolationException.class,
                () -> scheduling.createTemporaryClosure("branch-za", new CreateTemporaryBranchClosureCommand(
                        "closure-blank", start, start.plusSeconds(1), " ")));
        assertTrue(closures.findAll().isEmpty());
    }

    @Test
    void inactiveBranchOrBusinessClosesOtherwiseOpenBranchAndDiscoveryFlagIsIndependent() {
        createBranch("branch-private", "Africa/Johannesburg", false);
        replaceWithMondayHours("branch-private");
        Instant openInstant = Instant.parse("2030-01-07T08:00:00Z");

        assertTrue(scheduling.getOpenStatus("branch-private", openInstant).open());
        marketplace.deactivateBranch("branch-private");
        assertFalse(scheduling.getOpenStatus("branch-private", openInstant).open());
        marketplace.activateBranch("branch-private");
        marketplace.deactivateBusiness("business-001");
        BranchOpenStatusSnapshot inactiveBusiness = scheduling.getOpenStatus("branch-private", openInstant);
        assertTrue(inactiveBusiness.withinWeeklyHours());
        assertFalse(inactiveBusiness.effectiveActive());
        assertFalse(inactiveBusiness.open());
    }

    @Test
    void everyUseCaseRejectsUnknownBranchesAndUnknownClosures() {
        assertThrows(ResourceNotFoundException.class, () -> scheduling.getOperatingSchedule("missing"));
        assertThrows(ResourceNotFoundException.class, () -> scheduling.replaceOperatingSchedule(
                "missing", new ReplaceOperatingScheduleCommand(List.of())));
        assertThrows(ResourceNotFoundException.class, () -> scheduling.listTemporaryClosures("missing"));
        assertThrows(ResourceNotFoundException.class, () -> scheduling.createTemporaryClosure(
                "missing", new CreateTemporaryBranchClosureCommand(
                        "closure", Instant.EPOCH, Instant.EPOCH.plusSeconds(1), "Reason")));
        assertThrows(ResourceNotFoundException.class,
                () -> scheduling.getOpenStatus("missing", Instant.EPOCH));
        assertThrows(ResourceNotFoundException.class,
                () -> scheduling.cancelTemporaryClosure("missing"));
    }

    private void replaceWithMondayHours(String branchId) {
        scheduling.replaceOperatingSchedule(branchId, new ReplaceOperatingScheduleCommand(List.of(
                new WeeklyOperatingIntervalCommand(
                        DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))
        )));
    }

    private void createBranch(String branchId, String timezone, boolean discoverable) {
        marketplace.createBranch("business-001", new CreateBranchCommand(
                branchId, "Branch", "1 Main Road", null, "Cape Town", "Western Cape", "8001", "ZA",
                BigDecimal.valueOf(-33.9249), BigDecimal.valueOf(18.4241), timezone, discoverable));
    }
}
