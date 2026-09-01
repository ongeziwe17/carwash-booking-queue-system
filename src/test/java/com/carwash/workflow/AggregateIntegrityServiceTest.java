package com.carwash.workflow;

import com.carwash.booking.application.BookingManagementService;
import com.carwash.booking.application.BranchAvailabilityDecisionService;
import com.carwash.catalog.application.ServiceCatalogService;
import com.carwash.catalog.application.CreateServiceOfferingCommand;
import com.carwash.catalog.application.ServiceDefinitionUsageQuery;
import com.carwash.catalog.application.ServiceOfferingService;
import com.carwash.marketplace.application.CreateBranchCommand;
import com.carwash.marketplace.application.MarketplaceManagementService;
import com.carwash.marketplace.application.BranchSchedulingService;
import com.carwash.marketplace.application.ReplaceOperatingScheduleCommand;
import com.carwash.marketplace.application.WeeklyOperatingIntervalCommand;
import com.carwash.marketplace.application.RegisterBusinessCommand;
import com.carwash.identity.application.UserManagementService;
import com.carwash.notification.application.NotificationManagementService;
import com.carwash.notification.infrastructure.AtomicNotificationIdGenerator;
import com.carwash.queue.application.QueueManagementService;
import com.carwash.queue.application.QueueOrderingService;
import com.carwash.vehicle.application.VehicleManagementService;

import com.carwash.booking.application.BookingPolicyProperties;
import com.carwash.booking.application.BookingSlotPolicyService;
import com.carwash.notification.application.NotificationPolicyProperties;
import com.carwash.queue.application.QueuePolicyProperties;
import com.carwash.booking.domain.Booking;
import com.carwash.identity.domain.Role;
import com.carwash.catalog.domain.Service;
import com.carwash.identity.domain.User;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.booking.infrastructure.InMemoryBookingRepository;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import com.carwash.notification.infrastructure.InMemoryNotificationRepository;
import com.carwash.queue.infrastructure.InMemoryQueueEntryRepository;
import com.carwash.catalog.infrastructure.InMemoryServiceRepository;
import com.carwash.catalog.infrastructure.InMemoryServiceOfferingRepository;
import com.carwash.marketplace.infrastructure.InMemoryCarWashBusinessRepository;
import com.carwash.marketplace.infrastructure.InMemoryCarWashBranchRepository;
import com.carwash.marketplace.infrastructure.InMemoryBranchOperatingScheduleRepository;
import com.carwash.marketplace.infrastructure.InMemoryTemporaryBranchClosureRepository;
import com.carwash.identity.infrastructure.InMemoryUserRepository;
import com.carwash.vehicle.infrastructure.InMemoryVehicleRepository;
import com.carwash.access.application.UserCredentialService;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.testsupport.TestAccess;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AggregateIntegrityServiceTest {

    private InMemoryDataCoordinator coordinator;
    private InMemoryUserRepository users;
    private InMemoryVehicleRepository vehicles;
    private InMemoryServiceRepository services;
    private InMemoryBookingRepository bookings;
    private InMemoryQueueEntryRepository queues;
    private InMemoryNotificationRepository notifications;
    private InMemoryServiceOfferingRepository serviceOfferings;
    private MarketplaceManagementService marketplace;
    private ServiceOfferingService offeringService;
    private VehicleManagementService vehicleManagement;
    private ServiceCatalogService serviceCatalog;
    private BookingManagementService bookingManagement;
    private QueueManagementService queueManagement;
    private UserManagementService userManagement;

    @BeforeEach
    void setUp() {
        coordinator = new InMemoryDataCoordinator();
        users = new InMemoryUserRepository();
        vehicles = new InMemoryVehicleRepository();
        services = new InMemoryServiceRepository();
        bookings = new InMemoryBookingRepository();
        queues = new InMemoryQueueEntryRepository();
        notifications = new InMemoryNotificationRepository();
        serviceOfferings = new InMemoryServiceOfferingRepository();
        Clock clock = Clock.fixed(Instant.parse("2089-01-15T12:00:00Z"), ZoneOffset.UTC);
        NotificationManagementService notificationManagement = new NotificationManagementService(
                notifications, users, bookings, coordinator, new AtomicNotificationIdGenerator(),
                new NotificationPolicyProperties(10), clock);
        InMemoryCarWashBusinessRepository businesses = new InMemoryCarWashBusinessRepository();
        InMemoryCarWashBranchRepository branches = new InMemoryCarWashBranchRepository();
        marketplace = new MarketplaceManagementService(businesses, branches, coordinator, clock);
        BranchSchedulingService schedules = new BranchSchedulingService(
                businesses, branches, new InMemoryBranchOperatingScheduleRepository(),
                new InMemoryTemporaryBranchClosureRepository(), coordinator, clock);
        String businessId = "aggregate-business";
        marketplace.registerBusiness(TestAccess.platformAdministrator(), new RegisterBusinessCommand(
                businessId, "Aggregate Wash", "aggregate-wash@example.test", "+27821234567", null));
        marketplace.createBranch(TestAccess.platformAdministrator(), businessId, new CreateBranchCommand(
                "aggregate-branch", "Aggregate Branch", "1 Test Street", null, "Cape Town", "Western Cape",
                "8001", "ZA", new BigDecimal("-33.9249"), new BigDecimal("18.4241"),
                "Africa/Johannesburg", true));
        schedules.replaceOperatingSchedule(TestAccess.platformAdministrator(), "aggregate-branch",
                new ReplaceOperatingScheduleCommand(
                java.util.Arrays.stream(DayOfWeek.values())
                        .map(day -> new WeeklyOperatingIntervalCommand(
                                day, LocalTime.of(8, 0), LocalTime.of(17, 0)))
                        .toList()));
        ServiceDefinitionUsageQuery usage = new ServiceDefinitionUsageQuery() {
            public boolean referencedByBooking(String serviceId) { return bookings.existsByServiceId(serviceId); }
            public boolean referencedByQueue(String serviceId) { return queues.existsByServiceId(serviceId); }
        };
        serviceCatalog = new ServiceCatalogService(services, serviceOfferings, usage, coordinator);
        offeringService = new ServiceOfferingService(
                serviceOfferings, services, marketplace, coordinator, clock);
        QueueOrderingService queueOrdering = new QueueOrderingService(
                queues, coordinator, new QueuePolicyProperties(Duration.ofMinutes(10)), offeringService);
        vehicleManagement = new VehicleManagementService(vehicles, users, bookings, coordinator);
        BookingPolicyProperties bookingPolicy = new BookingPolicyProperties(1, Duration.ZERO);
        bookingManagement = new BookingManagementService(
                bookings, users, vehicles, serviceCatalog, offeringService, marketplace, queues,
                notifications, notificationManagement, queueOrdering, coordinator,
                bookingPolicy, new BookingSlotPolicyService(bookings, bookingPolicy, clock),
                new BranchAvailabilityDecisionService(
                        bookings, marketplace, schedules, offeringService, serviceCatalog, bookingPolicy, clock),
                clock);
        queueManagement = new QueueManagementService(
                queues, bookings, offeringService, marketplace, notificationManagement, coordinator, queueOrdering, clock);
        userManagement = new UserManagementService(users, mock(UserCredentialService.class), vehicles,
                bookings, notifications, coordinator);
    }

    @Test
    void aggregateCollectionsStaySynchronizedThroughLifecycle() {
        User user = activeUser("aggregate-user", "aggregate@example.test");
        assertTrue(users.insert(user));
        Service service = service("aggregate-service", "Exterior");
        assertTrue(services.insert(service));
        offeringService.createOffering(TestAccess.platformAdministrator(), "aggregate-branch", new CreateServiceOfferingCommand(
                "aggregate-offering", service.getServiceId(), BigDecimal.TEN, 30, 2));
        Vehicle vehicle = vehicleManagement.createVehicle(TestAccess.platformAdministrator(), "aggregate-user",
                "aggregate-vehicle", "ABC123", "SEDAN",
                "Toyota", "Corolla", "White", "");
        assertSame(vehicle, vehicles.findById("aggregate-vehicle").orElseThrow());
        assertEquals(1, user.getVehicles().size());
        Booking booking = bookingManagement.createBooking(TestAccess.platformAdministrator(),
                "aggregate-booking", "aggregate-user", "aggregate-vehicle",
                "aggregate-branch", "aggregate-offering", TestDates.futureDays(1), "");
        assertSame(booking, bookings.findById("aggregate-booking").orElseThrow());
        assertEquals(1, user.getBookings().size());
        bookingManagement.confirmBooking(TestAccess.platformAdministrator(), "aggregate-booking");
        var queueEntry = queueManagement.createQueueEntry(TestAccess.platformAdministrator(),
                "aggregate-queue", "aggregate-booking", "aggregate-service");
        assertEquals(queueEntry.getQueueEntryId(), booking.getQueueEntry().getQueueEntryId());
        assertSame(queues.findById(queueEntry.getQueueEntryId()).orElseThrow(), booking.getQueueEntry());
        assertThrows(BusinessRuleViolationException.class,
                () -> vehicleManagement.deleteVehicle(TestAccess.platformAdministrator(), "aggregate-vehicle"));
        assertThrows(BusinessRuleViolationException.class, () -> serviceCatalog.deleteService("aggregate-service"));
        assertThrows(BusinessRuleViolationException.class,
                () -> bookingManagement.deleteBooking(TestAccess.platformAdministrator(), "aggregate-booking"));
        queueManagement.deleteQueueEntry(TestAccess.platformAdministrator(), "aggregate-queue");
        assertNull(booking.getQueueEntry());
        bookingManagement.cancelBooking(TestAccess.platformAdministrator(), "aggregate-booking");
        assertFalse(notifications.findByBookingId("aggregate-booking").isEmpty());
        bookingManagement.deleteBooking(TestAccess.platformAdministrator(), "aggregate-booking");
        vehicleManagement.deleteVehicle(TestAccess.platformAdministrator(), "aggregate-vehicle");
        serviceCatalog.deactivateService("aggregate-service");
        userManagement.deleteUser("aggregate-user");
        assertTrue(users.findById("aggregate-user").isEmpty());
    }

    @Test
    void concurrentVehicleCreationKeepsRepositoryAndOwnerAggregateAligned() throws Exception {
        User user = activeUser("concurrent-user", "concurrent@example.test");
        assertTrue(users.insert(user));
        int count = 16;
        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Vehicle>> futures = java.util.stream.IntStream.range(0, count)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                        return vehicleManagement.createVehicle(TestAccess.platformAdministrator(), user.getUserId(),
                                "concurrent-vehicle-" + index,
                                "CONC" + index, "SEDAN", "Brand", "Model", "White", "");
                    })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<Vehicle> future : futures) assertNotNull(future.get(5, TimeUnit.SECONDS));
            assertEquals(count, vehicles.findByUserId(user.getUserId()).size());
            assertEquals(new HashSet<>(vehicles.findByUserId(user.getUserId()).stream().map(Vehicle::getVehicleId).toList()),
                    new HashSet<>(user.getVehicles().stream().map(Vehicle::getVehicleId).toList()));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void vehicleUpdateRechecksOwnerPlateUniquenessWithoutMutationOnFailure() {
        User user = activeUser("plate-user", "plate@example.test");
        assertTrue(users.insert(user));
        vehicleManagement.createVehicle(TestAccess.platformAdministrator(), user.getUserId(),
                "plate-vehicle-one", "ABC123", "SEDAN", "Brand", "One", "White", "");
        vehicleManagement.createVehicle(TestAccess.platformAdministrator(), user.getUserId(),
                "plate-vehicle-two", "XYZ999", "SUV", "Brand", "Two", "Black", "");
        assertThrows(BusinessRuleViolationException.class,
                () -> vehicleManagement.updateVehicle(TestAccess.platformAdministrator(), "plate-vehicle-one",
                        " xyz999 ", "SEDAN", "Changed", "Changed", "Blue", ""));
        Vehicle unchanged = vehicles.findById("plate-vehicle-one").orElseThrow();
        assertEquals("ABC123", unchanged.getPlateNumber());
        Vehicle retained = vehicleManagement.updateVehicle(TestAccess.platformAdministrator(), "plate-vehicle-one",
                " abc123 ", "SEDAN", "Updated", "One", "Silver", "");
        assertEquals("abc123", retained.getPlateNumber());
    }

    @Test
    void terminalBookingsAndNonWaitingQueueEntriesRejectMutation() {
        User user = activeUser("state-user", "state@example.test");
        assertTrue(users.insert(user));
        Service stateService = service("state-service", "State");
        assertTrue(services.insert(stateService));
        offeringService.createOffering(TestAccess.platformAdministrator(), "aggregate-branch", new CreateServiceOfferingCommand(
                "state-offering", stateService.getServiceId(), BigDecimal.TEN, 30, 2));
        vehicleManagement.createVehicle(TestAccess.platformAdministrator(), user.getUserId(),
                "state-vehicle", "STATE1", "SEDAN", "Brand", "Model", "White", "");
        Booking booking = bookingManagement.createBooking(TestAccess.platformAdministrator(),
                "state-booking", user.getUserId(), "state-vehicle", "aggregate-branch", "state-offering",
                TestDates.futureDays(2), "");
        bookingManagement.confirmBooking(TestAccess.platformAdministrator(), "state-booking");
        assertTrue(booking.startService());
        assertTrue(bookings.update(booking));
        assertThrows(BusinessRuleViolationException.class,
                () -> bookingManagement.updateBooking(TestAccess.platformAdministrator(),
                        "state-booking", "state-vehicle", "state-offering", ""));
        Booking waiting = bookingManagement.createBooking(TestAccess.platformAdministrator(),
                "state-queue-booking", user.getUserId(), "state-vehicle", "aggregate-branch", "state-offering",
                TestDates.futureDays(4), "");
        bookingManagement.confirmBooking(TestAccess.platformAdministrator(), waiting.getBookingId());
        var queueEntry = queueManagement.createQueueEntry(TestAccess.platformAdministrator(),
                "state-queue", waiting.getBookingId(), "state-service");
        queueManagement.callQueueEntry(TestAccess.platformAdministrator(), queueEntry.getQueueEntryId());
        assertThrows(BusinessRuleViolationException.class,
                () -> queueManagement.updatePosition(
                        TestAccess.platformAdministrator(), queueEntry.getQueueEntryId(), 2));
        assertThrows(BusinessRuleViolationException.class,
                () -> queueManagement.deleteQueueEntry(
                        TestAccess.platformAdministrator(), queueEntry.getQueueEntryId()));
    }

    @Test
    void duplicateCreationFailsBeforeAggregateCollectionsChange() {
        User user = activeUser("duplicate-user", "duplicate@example.test");
        assertTrue(users.insert(user));
        vehicleManagement.createVehicle(TestAccess.platformAdministrator(), user.getUserId(),
                "duplicate-vehicle", "DUP1", "SEDAN", "Brand", "Model", "White", "");
        assertThrows(BusinessRuleViolationException.class,
                () -> vehicleManagement.createVehicle(TestAccess.platformAdministrator(), user.getUserId(),
                        "duplicate-vehicle", "DUP2", "SUV", "Other", "Other", "Black", ""));
        assertEquals(1, vehicles.findAll().size());
        assertEquals(1, user.getVehicles().size());
        assertEquals("DUP1", vehicles.findById("duplicate-vehicle").orElseThrow().getPlateNumber());
    }

    private User activeUser(String id, String email) {
        User user = User.withEncodedPassword(id, "Test User", email, "0821234567", "encoded",
                new Role("customer", "CUSTOMER", "Customer", null));
        user.registerAccount();
        return user;
    }

    private Service service(String id, String name) {
        return new Service(id, name, "Description", BigDecimal.TEN, 30);
    }
}
