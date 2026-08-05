package com.carwash.service;

import com.carwash.domain.Booking;
import com.carwash.domain.Role;
import com.carwash.domain.Service;
import com.carwash.domain.User;
import com.carwash.domain.Vehicle;
import com.carwash.repository.inmemory.InMemoryBookingRepository;
import com.carwash.repository.inmemory.InMemoryDataCoordinator;
import com.carwash.repository.inmemory.InMemoryNotificationRepository;
import com.carwash.repository.inmemory.InMemoryQueueEntryRepository;
import com.carwash.repository.inmemory.InMemoryServiceRepository;
import com.carwash.repository.inmemory.InMemoryUserRepository;
import com.carwash.repository.inmemory.InMemoryVehicleRepository;
import com.carwash.security.UserCredentialService;
import com.carwash.service.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        NotificationManagementService notificationManagement =
                new NotificationManagementService(notifications, users, bookings, coordinator);
        vehicleManagement = new VehicleManagementService(vehicles, users, bookings, coordinator);
        serviceCatalog = new ServiceCatalogService(services, bookings, queues, coordinator);
        bookingManagement = new BookingManagementService(bookings, users, vehicles, services, queues,
                notifications, notificationManagement, coordinator);
        queueManagement = new QueueManagementService(queues, bookings, services, notificationManagement, coordinator);
        userManagement = new UserManagementService(users, mock(UserCredentialService.class), vehicles,
                bookings, notifications, coordinator);
    }

    @Test
    void aggregateCollectionsStaySynchronizedThroughLifecycle() {
        User user = activeUser("u-1", "user@example.com");
        assertTrue(users.insert(user));
        Service service = service("s-1", "Exterior");
        assertTrue(services.insert(service));
        Vehicle vehicle = vehicleManagement.createVehicle("u-1", "v-1", "ABC123", "SEDAN",
                "Toyota", "Corolla", "White", "");
        assertSame(vehicle, vehicles.findById("v-1").orElseThrow());
        assertEquals(1, user.getVehicles().size());
        Booking booking = bookingManagement.createBooking("b-1", "u-1", "v-1", "s-1",
                LocalDateTime.now().plusDays(1), "");
        assertSame(booking, bookings.findById("b-1").orElseThrow());
        assertSame(user, booking.getUser());
        assertSame(vehicle, booking.getVehicle());
        assertSame(service, booking.getService());
        assertEquals(1, user.getBookings().size());
        var queueEntry = queueManagement.createQueueEntry("q-1", "b-1", "s-1", 1);
        assertSame(queueEntry, queues.findById("q-1").orElseThrow());
        assertSame(queueEntry, booking.getQueueEntry());
        assertThrows(BusinessRuleViolationException.class, () -> vehicleManagement.deleteVehicle("v-1"));
        assertThrows(BusinessRuleViolationException.class, () -> serviceCatalog.deleteService("s-1"));
        assertThrows(BusinessRuleViolationException.class, () -> bookingManagement.deleteBooking("b-1"));
        queueManagement.deleteQueueEntry("q-1");
        assertTrue(queues.findById("q-1").isEmpty());
        assertNull(booking.getQueueEntry());
        bookingManagement.cancelBooking("b-1", "u-1");
        assertFalse(notifications.findByBookingId("b-1").isEmpty());
        bookingManagement.deleteBooking("b-1");
        assertTrue(bookings.findById("b-1").isEmpty());
        assertTrue(user.getBookings().isEmpty());
        assertTrue(notifications.findByBookingId("b-1").isEmpty());
        vehicleManagement.deleteVehicle("v-1");
        assertTrue(vehicles.findById("v-1").isEmpty());
        assertTrue(user.getVehicles().isEmpty());
        serviceCatalog.deleteService("s-1");
        assertTrue(services.findById("s-1").isEmpty());
        userManagement.deleteUser("u-1");
        assertTrue(users.findById("u-1").isEmpty());
        assertTrue(notifications.findByUserId("u-1").isEmpty());
    }

    @Test
    void vehicleUpdateRechecksOwnerPlateUniquenessWithoutMutationOnFailure() {
        User user = activeUser("u-plate", "plate@example.com");
        assertTrue(users.insert(user));
        vehicleManagement.createVehicle(user.getUserId(), "v-1", "ABC123", "SEDAN", "Brand", "One", "White", "");
        vehicleManagement.createVehicle(user.getUserId(), "v-2", "XYZ999", "SUV", "Brand", "Two", "Black", "");
        assertThrows(BusinessRuleViolationException.class,
                () -> vehicleManagement.updateVehicle("v-1", " xyz999 ", "SEDAN", "Changed", "Changed", "Blue", ""));
        Vehicle unchanged = vehicles.findById("v-1").orElseThrow();
        assertEquals("ABC123", unchanged.getPlateNumber());
        assertEquals("Brand", unchanged.getBrand());
        Vehicle retainedPlate = vehicleManagement.updateVehicle("v-1", " abc123 ", "SEDAN",
                "Updated", "One", "Silver", "");
        assertEquals("abc123", retainedPlate.getPlateNumber());
    }

    @Test
    void terminalBookingsAndNonWaitingQueueEntriesRejectMutation() {
        User user = activeUser("u-state", "state@example.com");
        assertTrue(users.insert(user));
        assertTrue(services.insert(service("s-state", "State")));
        vehicleManagement.createVehicle(user.getUserId(), "v-state", "STATE1", "SEDAN",
                "Brand", "Model", "White", "");
        Booking booking = bookingManagement.createBooking("b-state", user.getUserId(), "v-state", "s-state",
                LocalDateTime.now().plusDays(2), "");
        bookingManagement.confirmBooking("b-state");
        assertTrue(booking.startService());
        assertTrue(bookings.update(booking));
        assertThrows(BusinessRuleViolationException.class,
                () -> bookingManagement.updateBooking("b-state", "v-state", "s-state",
                        LocalDateTime.now().plusDays(3), ""));
        Booking waitingBooking = bookingManagement.createBooking("b-queue-state", user.getUserId(),
                "v-state", "s-state", LocalDateTime.now().plusDays(4), "");
        var queueEntry = queueManagement.createQueueEntry("q-state", waitingBooking.getBookingId(), "s-state", 1);
        queueManagement.callNext(queueEntry.getQueueEntryId());
        assertThrows(BusinessRuleViolationException.class,
                () -> queueManagement.updatePosition(queueEntry.getQueueEntryId(), 2));
        assertThrows(BusinessRuleViolationException.class,
                () -> queueManagement.deleteQueueEntry(queueEntry.getQueueEntryId()));
    }

    @Test
    void duplicateCreationFailsBeforeAggregateCollectionsChange() {
        User user = activeUser("u-duplicate", "duplicate@example.com");
        assertTrue(users.insert(user));
        vehicleManagement.createVehicle(user.getUserId(), "v-duplicate", "DUP1", "SEDAN",
                "Brand", "Model", "White", "");
        assertThrows(BusinessRuleViolationException.class,
                () -> vehicleManagement.createVehicle(user.getUserId(), "v-duplicate", "DUP2", "SUV",
                        "Other", "Other", "Black", ""));
        assertEquals(1, vehicles.findAll().size());
        assertEquals(1, user.getVehicles().size());
        assertEquals("DUP1", vehicles.findById("v-duplicate").orElseThrow().getPlateNumber());
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
