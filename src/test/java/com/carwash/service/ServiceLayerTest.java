package com.carwash.service;

import com.carwash.domain.*;
import com.carwash.enums.BookingStatus;
import com.carwash.repository.inmemory.*;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ServiceLayerTest {

    private UserManagementService userService;
    private VehicleManagementService vehicleService;
    private ServiceCatalogService catalogService;
    private BookingManagementService bookingService;
    private QueueManagementService queueService;

    @BeforeEach
    void setup() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryVehicleRepository vehicleRepository = new InMemoryVehicleRepository();
        InMemoryServiceRepository serviceRepository = new InMemoryServiceRepository();
        InMemoryBookingRepository bookingRepository = new InMemoryBookingRepository();
        InMemoryQueueEntryRepository queueRepository = new InMemoryQueueEntryRepository();

        userService = new UserManagementService(userRepository);
        vehicleService = new VehicleManagementService(vehicleRepository, userRepository);
        catalogService = new ServiceCatalogService(serviceRepository);
        bookingService = new BookingManagementService(bookingRepository, userRepository, vehicleRepository, serviceRepository);
        queueService = new QueueManagementService(queueRepository, bookingRepository, serviceRepository);
    }

    @Test
    void userCreationSucceeds() {
        User user = new User("u1", "Jane Doe", "jane@example.com", "123", "hash", null);
        assertEquals("u1", userService.createUser(user).getUserId());
    }

    @Test
    void userCreationFailsWithBlankEmail() {
        User user = new User("u1", "Jane Doe", " ", "123", "hash", null);
        assertThrows(BusinessRuleViolationException.class, () -> userService.createUser(user));
    }

    @Test
    void userCreationFailsWithBlankFullName() {
        User user = new User("u1", " ", "jane@example.com", "123", "hash", null);
        assertThrows(BusinessRuleViolationException.class, () -> userService.createUser(user));
    }

    @Test
    void userLookupMissingIdThrows() {
        assertThrows(ResourceNotFoundException.class, () -> userService.findById("missing"));
    }

    @Test
    void vehicleCreationSucceeds() {
        userService.createUser(new User("u1", "Jane Doe", "jane@example.com", "123", "hash", null));
        Vehicle vehicle = new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", "");
        assertEquals("v1", vehicleService.createVehicle(vehicle, "u1").getVehicleId());
    }

    @Test
    void vehicleCreationFailsWithBlankPlate() {
        userService.createUser(new User("u1", "Jane Doe", "jane@example.com", "123", "hash", null));
        Vehicle vehicle = new Vehicle("v1", " ", "Sedan", "Toyota", "Corolla", "Blue", "");
        assertThrows(BusinessRuleViolationException.class, () -> vehicleService.createVehicle(vehicle, "u1"));
    }

    @Test
    void vehicleUpdateFailsWhenMissing() {
        Vehicle vehicle = new Vehicle("missing", "CA123", "Sedan", "Toyota", "Corolla", "Blue", "");
        assertThrows(ResourceNotFoundException.class, () -> vehicleService.updateVehicle(vehicle));
    }

    @Test
    void serviceCreationSucceeds() {
        Service service = new Service("s1", "Premium Wash", "desc", BigDecimal.TEN, 30);
        assertEquals("s1", catalogService.createService(service).getServiceId());
    }

    @Test
    void serviceCreationFailsWithInvalidPrice() {
        Service service = new Service("s1", "Premium Wash", "desc", BigDecimal.valueOf(-1), 30);
        assertThrows(BusinessRuleViolationException.class, () -> catalogService.createService(service));
    }

    @Test
    void serviceCreationFailsWithInvalidDuration() {
        Service service = new Service("s1", "Premium Wash", "desc", BigDecimal.TEN, 0);
        assertThrows(BusinessRuleViolationException.class, () -> catalogService.createService(service));
    }

    @Test
    void deactivateServiceSucceeds() {
        Service service = new Service("s1", "Premium Wash", "desc", BigDecimal.TEN, 30);
        catalogService.createService(service);
        assertFalse(catalogService.deactivateService("s1").isActive());
    }

    @Test
    void bookingCreationSucceeds() {
        User user = new User("u1", "Jane Doe", "jane@example.com", "123", "hash", null);
        Vehicle vehicle = new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", "");
        Service service = new Service("s1", "Premium Wash", "desc", BigDecimal.TEN, 30);
        userService.createUser(user);
        vehicleService.createVehicle(vehicle, "u1");
        catalogService.createService(service);

        Booking booking = new Booking("b1", user, vehicle, service, LocalDateTime.now().plusDays(1), "none");
        assertEquals("b1", bookingService.createBooking(booking).getBookingId());
    }

    @Test
    void bookingCreationFailsWhenPast() {
        User user = new User("u1", "Jane Doe", "jane@example.com", "123", "hash", null);
        Vehicle vehicle = new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", "");
        Service service = new Service("s1", "Premium Wash", "desc", BigDecimal.TEN, 30);
        userService.createUser(user);
        vehicleService.createVehicle(vehicle, "u1");
        catalogService.createService(service);

        Booking booking = new Booking("b1", user, vehicle, service, LocalDateTime.now().minusHours(1), "none");
        assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void bookingConfirmationSucceeds() {
        Booking booking = createSavedBooking();
        assertEquals(BookingStatus.CONFIRMED, bookingService.confirmBooking(booking.getBookingId()).getStatus());
    }

    @Test
    void cancelledBookingCannotBeConfirmed() {
        Booking booking = createSavedBooking();
        bookingService.cancelBooking(booking.getBookingId());
        assertThrows(BusinessRuleViolationException.class, () -> bookingService.confirmBooking(booking.getBookingId()));
    }

    @Test
    void queueEntryCreationSucceeds() {
        Booking booking = createSavedBooking();
        QueueEntry queueEntry = new QueueEntry("q1", booking, booking.getService(), 1);
        assertEquals("q1", queueService.createQueueEntry(queueEntry).getQueueEntryId());
    }

    @Test
    void queueEntryCreationFailsWithInvalidPosition() {
        Booking booking = createSavedBooking();
        QueueEntry queueEntry = new QueueEntry("q1", booking, booking.getService(), 1);
        queueEntry.setPosition(0);
        assertThrows(BusinessRuleViolationException.class, () -> queueService.createQueueEntry(queueEntry));
    }

    @Test
    void queueEntryCompletionRequiresServiceStart() {
        Booking booking = createSavedBooking();
        QueueEntry queueEntry = queueService.createQueueEntry(new QueueEntry("q1", booking, booking.getService(), 1));
        assertThrows(BusinessRuleViolationException.class, () -> queueService.completeQueueEntry(queueEntry.getQueueEntryId()));

        queueService.startService(queueEntry.getQueueEntryId());
        assertNotNull(queueService.completeQueueEntry(queueEntry.getQueueEntryId()).getCompletedAt());
    }

    @Test
    void missingQueueLookupThrows() {
        assertThrows(ResourceNotFoundException.class, () -> queueService.findById("missing"));
    }

    private Booking createSavedBooking() {
        User user = new User("u1", "Jane Doe", "jane@example.com", "123", "hash", null);
        Vehicle vehicle = new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", "");
        Service service = new Service("s1", "Premium Wash", "desc", BigDecimal.TEN, 30);
        userService.createUser(user);
        vehicleService.createVehicle(vehicle, "u1");
        catalogService.createService(service);
        Booking booking = new Booking("b1", user, vehicle, service, LocalDateTime.now().plusDays(1), "none");
        return bookingService.createBooking(booking);
    }
    // ==================== DUPLICATE EMAIL HANDLING TESTS ====================

    @Test
    void testCreateUser_DuplicateEmail_ThrowsException() {
        // Create first user
        User user1 = new User("u1", "Jane Doe", "jane@example.com", "123", "hash", null);
        userService.createUser(user1);
        
        // Try to create second user with same email
        User user2 = new User("u2", "John Doe", "jane@example.com", "456", "hash2", null);
        
        assertThrows(BusinessRuleViolationException.class, () -> {
            userService.createUser(user2);
        });
    }

    @Test
    void testUpdateUser_DuplicateEmail_ThrowsException() {
        // Create first user
        User user1 = new User("u1", "Jane Doe", "jane@example.com", "123", "hash", null);
        userService.createUser(user1);
        
        // Create second user with different email
        User user2 = new User("u2", "John Doe", "john@example.com", "456", "hash2", null);
        userService.createUser(user2);
        
        // Try to update user2 to user1's email
        user2.setEmail("jane@example.com");
        
        assertThrows(BusinessRuleViolationException.class, () -> {
            userService.updateUser(user2);
        });
    }

    @Test
    void testDuplicateEmailCaseInsensitive_ThrowsException() {
        // Create first user with mixed case email
        User user1 = new User("u1", "Jane Doe", "Test@Example.com", "123", "hash", null);
        userService.createUser(user1);
        
        // Try to create second user with same email different case
        User user2 = new User("u2", "John Doe", "test@example.com", "456", "hash2", null);
        
        assertThrows(BusinessRuleViolationException.class, () -> {
            userService.createUser(user2);
        });
    }
}