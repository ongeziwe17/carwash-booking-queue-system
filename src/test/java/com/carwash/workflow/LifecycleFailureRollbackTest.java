package com.carwash.workflow;

import com.carwash.booking.application.BookingManagementService;
import com.carwash.booking.application.BranchAvailabilityDecisionService;
import com.carwash.queue.application.QueueManagementService;
import com.carwash.queue.application.QueueOrderingService;

import com.carwash.booking.application.BookingPolicyProperties;
import com.carwash.queue.application.QueuePolicyProperties;
import com.carwash.booking.domain.Booking;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.catalog.domain.Service;
import com.carwash.catalog.application.ServiceDefinitionQuery;
import com.carwash.catalog.application.ServiceDefinitionSnapshot;
import com.carwash.catalog.application.ServiceOfferingQuery;
import com.carwash.catalog.application.ServiceOfferingSnapshot;
import com.carwash.catalog.domain.ServiceOfferingStatus;
import com.carwash.marketplace.application.BranchSnapshot;
import com.carwash.marketplace.application.BranchOpenStatusSnapshot;
import com.carwash.marketplace.application.BranchScheduleQuery;
import com.carwash.marketplace.application.BranchServiceWindowSnapshot;
import com.carwash.marketplace.application.BusinessSnapshot;
import com.carwash.marketplace.application.MarketplaceQuery;
import com.carwash.marketplace.domain.BranchStatus;
import com.carwash.identity.domain.User;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.queue.domain.QueueStatus;
import com.carwash.booking.infrastructure.InMemoryBookingRepository;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import com.carwash.notification.infrastructure.InMemoryNotificationRepository;
import com.carwash.queue.infrastructure.InMemoryQueueEntryRepository;
import com.carwash.catalog.infrastructure.InMemoryServiceRepository;
import com.carwash.identity.infrastructure.InMemoryUserRepository;
import com.carwash.vehicle.infrastructure.InMemoryVehicleRepository;
import com.carwash.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleFailureRollbackTest {

    @Test
    void startRestoresBothAggregatesWhenBookingUpdateFails() {
        Fixture fixture = new Fixture();
        Booking booking = fixture.confirmedBooking("start", 10, 1);
        QueueEntry queueEntry = fixture.queueEntry(booking, "start");
        fixture.queueService.callQueueEntry(queueEntry.getQueueEntryId());
        fixture.bookings.failNextUpdate();

        assertThrows(ResourceNotFoundException.class,
                () -> fixture.queueService.startService(queueEntry.getQueueEntryId()));

        assertEquals(QueueStatus.CALLED, queueEntry.getQueueStatus());
        assertNull(queueEntry.getStartedAt());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertSame(booking, queueEntry.getBooking());
    }

    @Test
    void completionRestoresLifecycleWhenBookingUpdateFailsAfterQueueMutation() {
        Fixture fixture = new Fixture();
        Booking booking = fixture.confirmedBooking("complete", 10, 2);
        QueueEntry queueEntry = fixture.queueEntry(booking, "complete");
        fixture.queueService.callQueueEntry(queueEntry.getQueueEntryId());
        fixture.queueService.startService(queueEntry.getQueueEntryId());
        int originalPosition = queueEntry.getPosition();
        int originalWait = queueEntry.getEstimatedWaitMin();
        fixture.bookings.failNextUpdate();

        assertThrows(ResourceNotFoundException.class,
                () -> fixture.queueService.completeQueueEntry(queueEntry.getQueueEntryId()));

        assertEquals(QueueStatus.IN_PROGRESS, queueEntry.getQueueStatus());
        assertNull(queueEntry.getCompletedAt());
        assertEquals(originalPosition, queueEntry.getPosition());
        assertEquals(originalWait, queueEntry.getEstimatedWaitMin());
        assertEquals(BookingStatus.IN_SERVICE, booking.getStatus());
    }

    @Test
    void completionRestoresAllMetricsWhenRebalanceUpdateFails() {
        Fixture fixture = new Fixture();
        Booking firstBooking = fixture.confirmedBooking("rebalance-first", 10, 3);
        Booking secondBooking = fixture.confirmedBooking("rebalance-second", 25, 4);
        QueueEntry first = fixture.queueEntry(firstBooking, "rebalance-first");
        QueueEntry second = fixture.queueEntry(secondBooking, "rebalance-second");
        fixture.queueService.callQueueEntry(first.getQueueEntryId());
        fixture.queueService.startService(first.getQueueEntryId());
        fixture.queues.failOnNthUpdate(2);

        assertThrows(ResourceNotFoundException.class,
                () -> fixture.queueService.completeQueueEntry(first.getQueueEntryId()));

        assertEquals(QueueStatus.IN_PROGRESS, first.getQueueStatus());
        assertEquals(BookingStatus.IN_SERVICE, firstBooking.getStatus());
        assertEquals(1, first.getPosition());
        assertEquals(0, first.getEstimatedWaitMin());
        assertEquals(2, second.getPosition());
        assertEquals(10, second.getEstimatedWaitMin());
    }

    @Test
    void cancellationRestoresRemovedQueueAssociationAndOrderingWhenBookingUpdateFails() {
        Fixture fixture = new Fixture();
        Booking firstBooking = fixture.confirmedBooking("cancel-first", 10, 5);
        Booking secondBooking = fixture.confirmedBooking("cancel-second", 25, 6);
        QueueEntry first = fixture.queueEntry(firstBooking, "cancel-first");
        QueueEntry second = fixture.queueEntry(secondBooking, "cancel-second");
        fixture.bookings.failNextUpdate();

        assertThrows(ResourceNotFoundException.class, () -> fixture.bookingService.cancelBooking(
                firstBooking.getBookingId(), firstBooking.getUser().getUserId()));

        assertEquals(BookingStatus.CONFIRMED, firstBooking.getStatus());
        assertTrue(fixture.queues.existsById(first.getQueueEntryId()));
        assertTrue(fixture.queues.existsActiveByBookingId(firstBooking.getBookingId()));
        assertSame(first, firstBooking.getQueueEntry());
        assertEquals(1, first.getPosition());
        assertEquals(0, first.getEstimatedWaitMin());
        assertEquals(2, second.getPosition());
        assertEquals(10, second.getEstimatedWaitMin());
        assertFalse(fixture.notifications.findByBookingId(firstBooking.getBookingId()).stream()
                .anyMatch(notification -> "BOOKING_CANCELLED".equals(notification.getType())));
    }

    @Test
    void queueCreationRestoresBookingAndExistingBranchMetricsWhenBookingUpdateFails() {
        Fixture fixture = new Fixture();
        Booking firstBooking = fixture.confirmedBooking("create-first", 10, 7);
        Booking secondBooking = fixture.confirmedBooking("create-second", 25, 8);
        QueueEntry first = fixture.queueEntry(firstBooking, "create-first");
        fixture.bookings.failNextUpdate();

        assertThrows(ResourceNotFoundException.class,
                () -> fixture.queueEntry(secondBooking, "create-second"));

        assertFalse(fixture.queues.existsById("queue-create-second"));
        assertNull(secondBooking.getQueueEntry());
        assertEquals(1, first.getPosition());
        assertEquals(0, first.getEstimatedWaitMin());
    }

    @Test
    void queueDeletionRestoresEntryAndBookingAssociationWhenBookingUpdateFails() {
        Fixture fixture = new Fixture();
        Booking booking = fixture.confirmedBooking("delete", 10, 9);
        QueueEntry queueEntry = fixture.queueEntry(booking, "delete");
        fixture.bookings.failNextUpdate();

        assertThrows(ResourceNotFoundException.class,
                () -> fixture.queueService.deleteQueueEntry(queueEntry.getQueueEntryId()));

        assertTrue(fixture.queues.existsById(queueEntry.getQueueEntryId()));
        assertSame(queueEntry, booking.getQueueEntry());
        assertEquals(QueueStatus.WAITING, queueEntry.getQueueStatus());
        assertEquals(1, queueEntry.getPosition());
        assertEquals(0, queueEntry.getEstimatedWaitMin());
    }

    private static final class Fixture {
        private final FailingBookingRepository bookings = new FailingBookingRepository();
        private final FailingQueueEntryRepository queues = new FailingQueueEntryRepository();
        private final InMemoryUserRepository users = new InMemoryUserRepository();
        private final InMemoryVehicleRepository vehicles = new InMemoryVehicleRepository();
        private final InMemoryServiceRepository services = new InMemoryServiceRepository();
        private final InMemoryNotificationRepository notifications = new InMemoryNotificationRepository();
        private final InMemoryDataCoordinator coordinator = new InMemoryDataCoordinator();
        private final Clock clock = Clock.fixed(Instant.parse("2089-01-15T12:00:00Z"), ZoneOffset.UTC);
        private final Map<String, ServiceOfferingSnapshot> offeringSnapshots = new HashMap<>();
        private final MarketplaceQuery marketplace = marketplaceQuery();
        private final ServiceOfferingQuery offeringQuery = offeringQuery();
        private final ServiceDefinitionQuery serviceQuery = serviceQuery();
        private final QueueOrderingService ordering = new QueueOrderingService(
                queues, coordinator, new QueuePolicyProperties(Duration.ofMinutes(10)), offeringQuery);
        private final BookingPolicyProperties bookingPolicy = new BookingPolicyProperties(5, Duration.ZERO);
        private final BookingManagementService bookingService = new BookingManagementService(
                bookings, users, vehicles, serviceQuery, offeringQuery, marketplace, queues, notifications, null,
                ordering, coordinator, bookingPolicy,
                new com.carwash.booking.application.BookingSlotPolicyService(bookings, bookingPolicy, clock),
                new BranchAvailabilityDecisionService(
                        bookings, marketplace, openSchedules(), offeringQuery, serviceQuery, bookingPolicy, clock),
                clock);
        private final QueueManagementService queueService = new QueueManagementService(
                queues, bookings, offeringQuery, marketplace, null, coordinator, ordering, clock);

        private Booking confirmedBooking(String suffix, int duration, int futureDay) {
            User user = User.withEncodedPassword(
                    "user-" + suffix, "Lifecycle User", suffix + "@example.test", "0821234567", "hash", null);
            user.registerAccount();
            assertTrue(users.insert(user));
            Vehicle vehicle = new Vehicle("vehicle-" + suffix, "PLATE-" + suffix, "SUV",
                    "Toyota", "Rav4", "Black", "");
            vehicle.setUserId(user.getUserId());
            assertTrue(vehicles.insert(vehicle));
            Service service = new Service("service-" + suffix, "Wash " + suffix,
                    "Lifecycle fixture", BigDecimal.TEN, duration);
            assertTrue(services.insert(service));
            String offeringId = "offering-" + suffix;
            offeringSnapshots.put(offeringId, new ServiceOfferingSnapshot(
                    offeringId, "branch-rollback", service.getServiceId(), service.getServiceName(),
                    service.getDescription(), service.getPrice(), duration, 2, ServiceOfferingStatus.ACTIVE,
                    true, true, LocalDateTime.now(clock), LocalDateTime.now(clock)));
            Booking booking = new Booking(
                    "booking-" + suffix, user, vehicle, "branch-rollback", offeringId, service,
                    LocalDateTime.now(clock).plusDays(futureDay), "");
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setCreatedAt(LocalDateTime.now(clock));
            assertTrue(bookings.insert(booking));
            user.addBooking(booking);
            assertTrue(users.update(user));
            return booking;
        }

        private QueueEntry queueEntry(Booking booking, String suffix) {
            QueueEntry response = queueService.createQueueEntry(
                    "queue-" + suffix, booking.getBookingId(), booking.getService().getServiceId());
            return queues.findById(response.getQueueEntryId()).orElseThrow();
        }

        private MarketplaceQuery marketplaceQuery() {
            return new MarketplaceQuery() {
                public Optional<BusinessSnapshot> findBusinessOptional(String businessId) { return Optional.empty(); }
                public Optional<BranchSnapshot> findBranchOptional(String branchId) {
                    if (!"branch-rollback".equals(branchId)) return Optional.empty();
                    return Optional.of(new BranchSnapshot(
                            branchId, "business-rollback", "Rollback Branch", "1 Test Street", null,
                            "Cape Town", "Western Cape", "8001", "ZA", BigDecimal.ZERO, BigDecimal.ZERO,
                            "Africa/Johannesburg", BranchStatus.ACTIVE, true, true, true,
                            LocalDateTime.now(clock), LocalDateTime.now(clock)));
                }
                public List<BranchSnapshot> findBranchesByBusiness(String businessId) { return List.of(); }
                public List<BranchSnapshot> findDiscoverableBranches() { return List.of(); }
            };
        }

        private ServiceOfferingQuery offeringQuery() {
            return new ServiceOfferingQuery() {
                public Optional<ServiceOfferingSnapshot> findOfferingOptional(String offeringId) {
                    return Optional.ofNullable(offeringSnapshots.get(offeringId));
                }
                public List<ServiceOfferingSnapshot> findOfferingsByBranch(String branchId) { return List.of(); }
                public List<ServiceOfferingSnapshot> findDiscoverableOfferingsByBranch(String branchId) {
                    return List.of();
                }
                public Optional<ServiceOfferingSnapshot> findOfferingByBranchAndService(
                        String branchId, String serviceId) { return Optional.empty(); }
            };
        }

        private ServiceDefinitionQuery serviceQuery() {
            return serviceId -> services.findById(serviceId).map(service -> new ServiceDefinitionSnapshot(
                    service.getServiceId(), service.getServiceName(), service.getDescription(), service.getPrice(),
                    service.getEstimatedDurationMin(), service.isActive(), service.getCreatedAt()));
        }

        private BranchScheduleQuery openSchedules() {
            java.time.ZoneId zone = java.time.ZoneId.of("Africa/Johannesburg");
            return new BranchScheduleQuery() {
                @Override
                public BranchOpenStatusSnapshot getOpenStatus(String branchId, Instant requestedAt) {
                    return new BranchOpenStatusSnapshot(
                            branchId, requestedAt, zone.getId(), requestedAt.atZone(zone),
                            true, true, false, true, null, null);
                }

                @Override
                public BranchServiceWindowSnapshot getServiceWindowStatus(
                        String branchId,
                        Instant startsAt,
                        Instant endsAt
                ) {
                    return new BranchServiceWindowSnapshot(
                            branchId, startsAt, endsAt, zone.getId(), startsAt.atZone(zone), endsAt.atZone(zone),
                            startsAt.atZone(zone), true, true, false, true, null, null);
                }
            };
        }
    }

    private static final class FailingBookingRepository extends InMemoryBookingRepository {
        private boolean failNextUpdate;

        void failNextUpdate() {
            failNextUpdate = true;
        }

        @Override
        public boolean update(Booking booking) {
            if (failNextUpdate) {
                failNextUpdate = false;
                return false;
            }
            return super.update(booking);
        }
    }

    private static final class FailingQueueEntryRepository extends InMemoryQueueEntryRepository {
        private int updatesUntilFailure = -1;

        void failOnNthUpdate(int updateNumber) {
            updatesUntilFailure = updateNumber;
        }

        @Override
        public boolean update(QueueEntry queueEntry) {
            if (updatesUntilFailure > 0 && --updatesUntilFailure == 0) {
                updatesUntilFailure = -1;
                return false;
            }
            return super.update(queueEntry);
        }
    }
}
