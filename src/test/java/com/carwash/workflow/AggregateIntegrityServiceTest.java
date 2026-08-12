package com.carwash.workflow;

import com.carwash.booking.application.BookingManagementService;
import com.carwash.catalog.application.ServiceCatalogService;
import com.carwash.identity.application.UserManagementService;
import com.carwash.notification.application.NotificationManagementService;
import com.carwash.notification.infrastructure.AtomicNotificationIdGenerator;
import com.carwash.queue.application.QueueManagementService;
import com.carwash.queue.application.QueueOrderingService;
import com.carwash.vehicle.application.VehicleManagementService;

import com.carwash.booking.application.BookingPolicyProperties;
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
import com.carwash.identity.infrastructure.InMemoryUserRepository;
import com.carwash.vehicle.infrastructure.InMemoryVehicleRepository;
import com.carwash.access.application.UserCredentialService;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
        Clock clock = Clock.fixed(Instant.parse("2089-01-15T12:00:00Z"), ZoneOffset.UTC);
        NotificationManagementService notificationManagement = new NotificationManagementService(
                notifications, users, bookings, coordinator, new AtomicNotificationIdGenerator(),
                new NotificationPolicyProperties(10), clock);
        QueueOrderingService queueOrdering = new QueueOrderingService(
                queues, coordinator, new QueuePolicyProperties(Duration.ofMinutes(10)));
        vehicleManagement = new VehicleManagementService(vehicles, users, bookings, coordinator);
        serviceCatalog = new ServiceCatalogService(services, bookings, queues, coordinator, queueOrdering);
        bookingManagement = new BookingManagementService(bookings, users, vehicles, services, queues,
                notifications, notificationManagement, queueOrdering, coordinator,
                new BookingPolicyProperties(1, Duration.ZERO), clock);
        queueManagement = new QueueManagementService(queues, bookings, services, notificationManagement, coordinator,
                queueOrdering, clock);
        userManagement = new UserManagementService(users, mock(UserCredentialService.class), vehicles,
                bookings, notifications, coordinator);
    }

    @Test
    void aggregateCollectionsStaySynchronizedThroughLifecycle() {
        User user = activeUser("aggregate-user", "aggregate@example.test");
        assertTrue(users.insert(user));
        Service service = service("aggregate-service", "Exterior");
        assertTrue(services.insert(service));
        Vehicle vehicle = vehicleManagement.createVehicle("aggregate-user", "aggregate-vehicle", "ABC123", "SEDAN",
                "Toyota", "Corolla", "White", "");
        assertSame(vehicle, vehicles.findById("aggregate-vehicle").orElseThrow());
        assertEquals(1, user.getVehicles().size());
        Booking booking = bookingManagement.createBooking("aggregate-booking", "aggregate-user", "aggregate-vehicle",
                "aggregate-service", TestDates.futureDays(1), "");
        assertSame(booking, bookings.findById("aggregate-booking").orElseThrow());
        assertEquals(1, user.getBookings().size());
        bookingManagement.confirmBooking("aggregate-booking");
        var queueEntry = queueManagement.createQueueEntry("aggregate-queue", "aggregate-booking", "aggregate-service");
        assertEquals(queueEntry.getQueueEntryId(), booking.getQueueEntry().getQueueEntryId());
        assertSame(queues.findById(queueEntry.getQueueEntryId()).orElseThrow(), booking.getQueueEntry());
        assertThrows(BusinessRuleViolationException.class, () -> vehicleManagement.deleteVehicle("aggregate-vehicle"));
        assertThrows(BusinessRuleViolationException.class, () -> serviceCatalog.deleteService("aggregate-service"));
        assertThrows(BusinessRuleViolationException.class, () -> bookingManagement.deleteBooking("aggregate-booking"));
        queueManagement.deleteQueueEntry("aggregate-queue");
        assertNull(booking.getQueueEntry());
        bookingManagement.cancelBooking("aggregate-booking", "aggregate-user");
        assertFalse(notifications.findByBookingId("aggregate-booking").isEmpty());
        bookingManagement.deleteBooking("aggregate-booking");
        vehicleManagement.deleteVehicle("aggregate-vehicle");
        serviceCatalog.deleteService("aggregate-service");
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
                        return vehicleManagement.createVehicle(user.getUserId(), "concurrent-vehicle-" + index,
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
        vehicleManagement.createVehicle(user.getUserId(), "plate-vehicle-one", "ABC123", "SEDAN", "Brand", "One", "White", "");
        vehicleManagement.createVehicle(user.getUserId(), "plate-vehicle-two", "XYZ999", "SUV", "Brand", "Two", "Black", "");
        assertThrows(BusinessRuleViolationException.class,
                () -> vehicleManagement.updateVehicle("plate-vehicle-one", " xyz999 ", "SEDAN", "Changed", "Changed", "Blue", ""));
        Vehicle unchanged = vehicles.findById("plate-vehicle-one").orElseThrow();
        assertEquals("ABC123", unchanged.getPlateNumber());
        Vehicle retained = vehicleManagement.updateVehicle("plate-vehicle-one", " abc123 ", "SEDAN", "Updated", "One", "Silver", "");
        assertEquals("abc123", retained.getPlateNumber());
    }

    @Test
    void terminalBookingsAndNonWaitingQueueEntriesRejectMutation() {
        User user = activeUser("state-user", "state@example.test");
        assertTrue(users.insert(user));
        assertTrue(services.insert(service("state-service", "State")));
        vehicleManagement.createVehicle(user.getUserId(), "state-vehicle", "STATE1", "SEDAN", "Brand", "Model", "White", "");
        Booking booking = bookingManagement.createBooking("state-booking", user.getUserId(), "state-vehicle", "state-service",
                TestDates.futureDays(2), "");
        bookingManagement.confirmBooking("state-booking");
        assertTrue(booking.startService());
        assertTrue(bookings.update(booking));
        assertThrows(BusinessRuleViolationException.class,
                () -> bookingManagement.updateBooking("state-booking", "state-vehicle", "state-service", ""));
        Booking waiting = bookingManagement.createBooking("state-queue-booking", user.getUserId(), "state-vehicle", "state-service",
                TestDates.futureDays(4), "");
        bookingManagement.confirmBooking(waiting.getBookingId());
        var queueEntry = queueManagement.createQueueEntry("state-queue", waiting.getBookingId(), "state-service");
        queueManagement.callQueueEntry(queueEntry.getQueueEntryId());
        assertThrows(BusinessRuleViolationException.class, () -> queueManagement.updatePosition(queueEntry.getQueueEntryId(), 2));
        assertThrows(BusinessRuleViolationException.class, () -> queueManagement.deleteQueueEntry(queueEntry.getQueueEntryId()));
    }

    @Test
    void duplicateCreationFailsBeforeAggregateCollectionsChange() {
        User user = activeUser("duplicate-user", "duplicate@example.test");
        assertTrue(users.insert(user));
        vehicleManagement.createVehicle(user.getUserId(), "duplicate-vehicle", "DUP1", "SEDAN", "Brand", "Model", "White", "");
        assertThrows(BusinessRuleViolationException.class,
                () -> vehicleManagement.createVehicle(user.getUserId(), "duplicate-vehicle", "DUP2", "SUV", "Other", "Other", "Black", ""));
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
