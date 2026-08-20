package com.carwash.booking.application;

import com.carwash.booking.domain.Booking;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.booking.infrastructure.InMemoryBookingRepository;
import com.carwash.catalog.application.ServiceDefinitionQuery;
import com.carwash.catalog.application.ServiceDefinitionSnapshot;
import com.carwash.catalog.application.ServiceOfferingQuery;
import com.carwash.catalog.application.ServiceOfferingSnapshot;
import com.carwash.catalog.domain.Service;
import com.carwash.catalog.domain.ServiceOfferingStatus;
import com.carwash.identity.domain.User;
import com.carwash.marketplace.application.BranchOpenStatusSnapshot;
import com.carwash.marketplace.application.BranchScheduleQuery;
import com.carwash.marketplace.application.BranchServiceWindowSnapshot;
import com.carwash.marketplace.application.BranchSnapshot;
import com.carwash.marketplace.application.BusinessSnapshot;
import com.carwash.marketplace.application.MarketplaceQuery;
import com.carwash.marketplace.domain.BranchStatus;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.vehicle.domain.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchAvailabilityDecisionServiceTest {

    private static final Instant START = Instant.parse("2030-01-07T08:00:00Z");
    private final InMemoryBookingRepository bookings = new InMemoryBookingRepository();
    private BranchAvailabilityDecisionService decisions;

    @BeforeEach
    void setUp() {
        decisions = service(true, true, true);
    }

    @Test
    void availableDecisionUsesOfferingTermsAndOverlappingCapacity() {
        BranchAvailabilityDecisionSnapshot empty = decisions.evaluate(
                "branch-1", "offering-1", START, null);
        assertTrue(empty.available());
        assertEquals(2, empty.concurrentCapacity());
        assertEquals(2, empty.capacityRemaining());
        assertEquals(new BigDecimal("125.50"), empty.price());
        assertEquals(45, empty.estimatedDurationMin());

        assertTrue(bookings.insert(booking("booking-1", "branch-1", "offering-1",
                LocalDateTime.ofInstant(START.plusSeconds(15 * 60), ZoneId.of("Africa/Johannesburg")),
                BookingStatus.CONFIRMED)));
        assertTrue(bookings.insert(booking("booking-other", "branch-other", "offering-1",
                LocalDateTime.ofInstant(START, ZoneId.of("Africa/Johannesburg")), BookingStatus.CONFIRMED)));
        assertTrue(bookings.insert(booking("booking-cancelled", "branch-1", "offering-1",
                LocalDateTime.ofInstant(START, ZoneId.of("Africa/Johannesburg")), BookingStatus.CANCELLED)));

        BranchAvailabilityDecisionSnapshot occupied = decisions.evaluate(
                "branch-1", "offering-1", START, null);
        assertEquals(1, occupied.occupiedCapacity());
        assertEquals(1, occupied.capacityRemaining());
        assertTrue(occupied.available());
    }

    @Test
    void fullCapacityIsExcludedAndReschedulingCanExcludeItself() {
        assertTrue(bookings.insert(booking("booking-1", "branch-1", "offering-1",
                LocalDateTime.ofInstant(START, ZoneId.of("Africa/Johannesburg")), BookingStatus.CREATED)));
        assertTrue(bookings.insert(booking("booking-2", "branch-1", "offering-1",
                LocalDateTime.ofInstant(START.plusSeconds(10 * 60), ZoneId.of("Africa/Johannesburg")),
                BookingStatus.IN_SERVICE)));

        BranchAvailabilityDecisionSnapshot full = decisions.evaluate("branch-1", "offering-1", START, null);
        assertFalse(full.available());
        assertEquals(BranchAvailabilityReason.CAPACITY_FULL, full.reason());
        assertEquals(0, full.capacityRemaining());

        BranchAvailabilityDecisionSnapshot excludingSelf = decisions.evaluate(
                "branch-1", "offering-1", START, "booking-1");
        assertTrue(excludingSelf.available());
        assertEquals(1, excludingSelf.capacityRemaining());
    }

    @Test
    void lifecycleAndCompleteWindowDecisionsAreShared() {
        assertEquals(BranchAvailabilityReason.INACTIVE_BRANCH,
                service(false, true, true).evaluate("branch-1", "offering-1", START, null).reason());
        assertEquals(BranchAvailabilityReason.INACTIVE_OFFERING,
                service(true, false, true).evaluate("branch-1", "offering-1", START, null).reason());
        assertEquals(BranchAvailabilityReason.INACTIVE_SERVICE,
                service(true, true, false).evaluate("branch-1", "offering-1", START, null).reason());

        BranchScheduleQuery closedWindow = schedules(false, null);
        BranchAvailabilityDecisionService closed = new BranchAvailabilityDecisionService(
                bookings, marketplace(true), closedWindow, offerings(true), definitions(true),
                policy(), clock());
        assertEquals(BranchAvailabilityReason.OUTSIDE_OPERATING_HOURS,
                closed.evaluate("branch-1", "offering-1", START, null).reason());
    }

    @Test
    void arbitraryWholeMinuteIntervalsAlignFromTheOperatingWindowStart() {
        ZoneId zone = ZoneId.of("Africa/Johannesburg");
        LocalDateTime opening = LocalDateTime.of(2030, 1, 7, 8, 0);
        BranchAvailabilityDecisionService fortyFiveMinutes = service(
                true, true, true,
                new BookingPolicyProperties(99, Duration.ZERO, LocalTime.of(8, 0),
                        LocalTime.of(17, 0), Duration.ofMinutes(45)),
                schedules(true, opening.atZone(zone)));

        assertTrue(fortyFiveMinutes.evaluate("branch-1", "offering-1",
                opening.atZone(zone).toInstant(), null).available());
        assertTrue(fortyFiveMinutes.evaluate("branch-1", "offering-1",
                opening.plusMinutes(45).atZone(zone).toInstant(), null).available());
        assertEquals(BranchAvailabilityReason.MISALIGNED_SLOT,
                fortyFiveMinutes.evaluate("branch-1", "offering-1",
                        opening.plusMinutes(15).atZone(zone).toInstant(), null).reason());

        LocalDateTime offsetOpening = LocalDateTime.of(2030, 1, 7, 8, 15);
        BranchAvailabilityDecisionService thirtyMinutes = service(
                true, true, true,
                new BookingPolicyProperties(99, Duration.ZERO, LocalTime.of(8, 0),
                        LocalTime.of(17, 0), Duration.ofMinutes(30)),
                schedules(true, offsetOpening.atZone(zone)));
        assertTrue(thirtyMinutes.evaluate("branch-1", "offering-1",
                offsetOpening.atZone(zone).toInstant(), null).available());
        assertTrue(thirtyMinutes.evaluate("branch-1", "offering-1",
                offsetOpening.plusMinutes(30).atZone(zone).toInstant(), null).available());
    }

    @Test
    void fractionalOperatingWindowPrecisionIsPreservedForAlignment() {
        ZoneId zone = ZoneId.of("Africa/Johannesburg");
        ZonedDateTime opening = LocalDateTime.of(2030, 1, 7, 8, 0, 0, 100_000_000).atZone(zone);
        BranchAvailabilityDecisionService precise = service(
                true, true, true,
                new BookingPolicyProperties(99, Duration.ZERO, LocalTime.of(8, 0),
                        LocalTime.of(17, 0), Duration.ofMinutes(45)),
                schedules(true, opening));

        assertTrue(precise.evaluate("branch-1", "offering-1", opening.toInstant(), null).available());
        assertTrue(precise.evaluate(
                "branch-1", "offering-1", opening.plusMinutes(45).toInstant(), null).available());
        assertEquals(BranchAvailabilityReason.MISALIGNED_SLOT,
                precise.evaluate("branch-1", "offering-1",
                        opening.plusMinutes(45).minusNanos(100_000_000).toInstant(), null).reason());
    }

    @Test
    void ambiguousBranchLocalStartsAreUnavailableForBothOverlapInstants() {
        ZoneId zone = ZoneId.of("America/New_York");
        LocalDateTime opening = LocalDateTime.of(2090, 11, 5, 0, 0);
        LocalDateTime ambiguous = LocalDateTime.of(2090, 11, 5, 1, 30);
        BranchAvailabilityDecisionService newYork = new BranchAvailabilityDecisionService(
                bookings,
                marketplace(true, zone.getId()),
                schedules(zone, true, opening.atZone(zone)),
                offerings(true),
                definitions(true),
                policy(),
                clock());

        List<ZoneOffset> overlapOffsets = zone.getRules().getValidOffsets(ambiguous);
        assertEquals(2, overlapOffsets.size());
        for (ZoneOffset offset : overlapOffsets) {
            BranchAvailabilityDecisionSnapshot decision = newYork.evaluate(
                    "branch-1", "offering-1", ambiguous.toInstant(offset), null);
            assertFalse(decision.available());
            assertEquals(BranchAvailabilityReason.INVALID_LOCAL_TIME, decision.reason());
        }

        assertTrue(newYork.evaluate("branch-1", "offering-1",
                LocalDateTime.of(2090, 11, 5, 0, 30).atZone(zone).toInstant(), null).available());
        assertTrue(newYork.evaluate("branch-1", "offering-1",
                LocalDateTime.of(2090, 11, 5, 2, 30).atZone(zone).toInstant(), null).available());
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> newYork.evaluateLocal("branch-1", "offering-1", ambiguous, null));
        assertEquals("Scheduled date/time is ambiguous in the branch timezone", exception.getMessage());

        LocalDateTime gap = LocalDateTime.of(2090, 3, 12, 2, 30);
        assertTrue(zone.getRules().getValidOffsets(gap).isEmpty());
        BusinessRuleViolationException gapException = assertThrows(
                BusinessRuleViolationException.class,
                () -> newYork.evaluateLocal("branch-1", "offering-1", gap, null));
        assertEquals("Scheduled date/time does not exist in the branch timezone", gapException.getMessage());
    }

    private BranchAvailabilityDecisionService service(
            boolean branchActive,
            boolean offeringActive,
            boolean serviceActive
    ) {
        return service(branchActive, offeringActive, serviceActive, policy(), schedules(
                true, LocalDateTime.of(2030, 1, 7, 8, 0).atZone(ZoneId.of("Africa/Johannesburg"))));
    }

    private BranchAvailabilityDecisionService service(
            boolean branchActive,
            boolean offeringActive,
            boolean serviceActive,
            BookingPolicyProperties bookingPolicy,
            BranchScheduleQuery schedules
    ) {
        return new BranchAvailabilityDecisionService(
                bookings, marketplace(branchActive), schedules, offerings(offeringActive), definitions(serviceActive),
                bookingPolicy, clock());
    }

    private BranchScheduleQuery schedules(boolean open, ZonedDateTime operatingWindowStartsAt) {
        return schedules(ZoneId.of("Africa/Johannesburg"), open, operatingWindowStartsAt);
    }

    private BranchScheduleQuery schedules(
            ZoneId zone,
            boolean open,
            ZonedDateTime operatingWindowStartsAt
    ) {
        return new BranchScheduleQuery() {
            @Override
            public BranchOpenStatusSnapshot getOpenStatus(String branchId, Instant requestedAt) {
                return new BranchOpenStatusSnapshot(
                        branchId, requestedAt, zone.getId(), requestedAt.atZone(zone),
                        true, open, false, open, null, null);
            }

            @Override
            public BranchServiceWindowSnapshot getServiceWindowStatus(
                    String branchId,
                    Instant startsAt,
                    Instant endsAt
            ) {
                return new BranchServiceWindowSnapshot(
                        branchId, startsAt, endsAt, zone.getId(), startsAt.atZone(zone), endsAt.atZone(zone),
                        operatingWindowStartsAt, true, open, false, open, null, null);
            }
        };
    }

    private MarketplaceQuery marketplace(boolean active) {
        return marketplace(active, "Africa/Johannesburg");
    }

    private MarketplaceQuery marketplace(boolean active, String timezone) {
        BranchSnapshot branch = new BranchSnapshot(
                "branch-1", "business-1", "Branch", "1 Main Road", null, "Cape Town", "Western Cape",
                "8001", "ZA", new BigDecimal("-33.9"), new BigDecimal("18.4"), timezone,
                active ? BranchStatus.ACTIVE : BranchStatus.INACTIVE, true, active, active,
                LocalDateTime.MIN, LocalDateTime.MIN);
        return new MarketplaceQuery() {
            public Optional<BusinessSnapshot> findBusinessOptional(String businessId) { return Optional.empty(); }
            public Optional<BranchSnapshot> findBranchOptional(String branchId) {
                return "branch-1".equals(branchId) ? Optional.of(branch) : Optional.empty();
            }
            public List<BranchSnapshot> findBranchesByBusiness(String businessId) { return List.of(branch); }
            public List<BranchSnapshot> findDiscoverableBranches() { return active ? List.of(branch) : List.of(); }
        };
    }

    private ServiceOfferingQuery offerings(boolean active) {
        ServiceOfferingSnapshot offering = new ServiceOfferingSnapshot(
                "offering-1", "branch-1", "service-1", "Premium Wash", "Description",
                new BigDecimal("125.50"), 45, 2, ServiceOfferingStatus.ACTIVE,
                active, active, LocalDateTime.MIN, LocalDateTime.MIN);
        return new ServiceOfferingQuery() {
            public Optional<ServiceOfferingSnapshot> findOfferingOptional(String offeringId) {
                return "offering-1".equals(offeringId) ? Optional.of(offering) : Optional.empty();
            }
            public List<ServiceOfferingSnapshot> findOfferingsByBranch(String branchId) { return List.of(offering); }
            public List<ServiceOfferingSnapshot> findDiscoverableOfferingsByBranch(String branchId) {
                return active ? List.of(offering) : List.of();
            }
            public Optional<ServiceOfferingSnapshot> findOfferingByBranchAndService(
                    String branchId, String serviceId) {
                return Optional.of(offering);
            }
        };
    }

    private ServiceDefinitionQuery definitions(boolean active) {
        return serviceId -> Optional.of(new ServiceDefinitionSnapshot(
                "service-1", "Premium Wash", "Description", BigDecimal.TEN, 30,
                active, LocalDateTime.MIN));
    }

    private Booking booking(
            String id,
            String branchId,
            String offeringId,
            LocalDateTime scheduledAt,
            BookingStatus status
    ) {
        User user = new User();
        user.setUserId("user-" + id);
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId("vehicle-" + id);
        Service service = new Service();
        service.setServiceId("service-1");
        Booking booking = new Booking(id, user, vehicle, branchId, offeringId, service, scheduledAt, "");
        booking.setStatus(status);
        booking.setCreatedAt(LocalDateTime.MIN);
        return booking;
    }

    private BookingPolicyProperties policy() {
        return new BookingPolicyProperties(99, Duration.ZERO);
    }

    private Clock clock() {
        return Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
    }
}
