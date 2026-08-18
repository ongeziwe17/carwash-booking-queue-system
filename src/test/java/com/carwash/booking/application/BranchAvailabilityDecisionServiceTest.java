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
import com.carwash.marketplace.application.BranchSnapshot;
import com.carwash.marketplace.application.BusinessSnapshot;
import com.carwash.marketplace.application.MarketplaceQuery;
import com.carwash.marketplace.domain.BranchStatus;
import com.carwash.vehicle.domain.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        BranchScheduleQuery closedWindow = (branchId, requestedAt) -> new BranchOpenStatusSnapshot(
                branchId, requestedAt, "Africa/Johannesburg", requestedAt.atZone(ZoneId.of("Africa/Johannesburg")),
                true, false, false, false, null, null);
        BranchAvailabilityDecisionService closed = new BranchAvailabilityDecisionService(
                bookings, marketplace(true), closedWindow, offerings(true), definitions(true),
                policy(), clock());
        assertEquals(BranchAvailabilityReason.OUTSIDE_OPERATING_HOURS,
                closed.evaluate("branch-1", "offering-1", START, null).reason());
    }

    private BranchAvailabilityDecisionService service(
            boolean branchActive,
            boolean offeringActive,
            boolean serviceActive
    ) {
        BranchScheduleQuery open = (branchId, requestedAt) -> new BranchOpenStatusSnapshot(
                branchId, requestedAt, "Africa/Johannesburg", requestedAt.atZone(ZoneId.of("Africa/Johannesburg")),
                true, true, false, true, null, null);
        return new BranchAvailabilityDecisionService(
                bookings, marketplace(branchActive), open, offerings(offeringActive), definitions(serviceActive),
                policy(), clock());
    }

    private MarketplaceQuery marketplace(boolean active) {
        BranchSnapshot branch = new BranchSnapshot(
                "branch-1", "business-1", "Branch", "1 Main Road", null, "Cape Town", "Western Cape",
                "8001", "ZA", new BigDecimal("-33.9"), new BigDecimal("18.4"), "Africa/Johannesburg",
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
