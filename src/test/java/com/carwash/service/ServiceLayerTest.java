package com.carwash.service;

import com.carwash.api.dto.DailySummaryReportResponse;
import com.carwash.domain.*;
import com.carwash.enums.BookingStatus;
import com.carwash.enums.AccountStatus;
import com.carwash.enums.QueueStatus;
import com.carwash.repository.inmemory.*;
import com.carwash.service.command.CreateUserCommand;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.carwash.config.PasswordSecurityProperties;
import com.carwash.security.UserCredentialService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServiceLayerTest {

    private UserManagementService userService;
    private VehicleManagementService vehicleService;
    private ServiceCatalogService catalogService;
    private BookingManagementService bookingService;
    private QueueManagementService queueService;
    private NotificationManagementService notificationService;
    private DailySummaryReportService dailySummaryReportService;

    @BeforeEach
    void setup() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryVehicleRepository vehicleRepository = new InMemoryVehicleRepository();
        InMemoryServiceRepository serviceRepository = new InMemoryServiceRepository();
        InMemoryBookingRepository bookingRepository = new InMemoryBookingRepository();
        InMemoryQueueEntryRepository queueRepository = new InMemoryQueueEntryRepository();
        InMemoryNotificationRepository notificationRepository = new InMemoryNotificationRepository();

        userService = new UserManagementService(userRepository, new UserCredentialService(
                new BCryptPasswordEncoder(4), new PasswordSecurityProperties(4, 12, 200)));
        vehicleService = new VehicleManagementService(vehicleRepository, userRepository);
        catalogService = new ServiceCatalogService(serviceRepository);
        notificationService = new NotificationManagementService(notificationRepository);
        bookingService = new BookingManagementService(bookingRepository, userRepository, vehicleRepository, serviceRepository, notificationService);
        queueService = new QueueManagementService(queueRepository, bookingRepository, serviceRepository, notificationService);
        dailySummaryReportService = new DailySummaryReportService(bookingRepository, queueRepository);
    }

    @Test
    void userCreationSucceeds() {
        User user = User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "hash", null);
        User created = register(user);
        assertEquals("u1", created.getUserId());
        assertEquals(AccountStatus.ACTIVE, created.getAccountStatus());
        assertNotNull(created.getCreatedAt());
    }

    @Test
    void userCreationNormalizesProfileAndEmail() {
        User created = register(User.withEncodedPassword(" u1 ", " Jane Doe ", " CUSTOMER@Example.COM ", " 123 ", "hash", null));

        assertEquals("u1", created.getUserId());
        assertEquals("Jane Doe", created.getFullName());
        assertEquals("customer@example.com", created.getEmail());
        assertEquals("123", created.getPhone());
    }

    @Test
    void userCreationRejectsCaseAndWhitespaceDuplicateEmail() {
        register(User.withEncodedPassword("u1", "Jane Doe", "customer@example.com", "123", "hash", null));

        assertThrows(BusinessRuleViolationException.class, () -> register(
                User.withEncodedPassword("u2", "John Doe", " CUSTOMER@EXAMPLE.COM ", "456", "hash", null)));
    }

    @Test
    void profileUpdatePreservesServerControlledAndSensitiveFields() {
        Role originalRole = new Role("customer", "CUSTOMER", "Customer", null);
        User created = register(User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "original-secret", originalRole));
        LocalDateTime createdAt = created.getCreatedAt();
        String encodedPassword = created.getEncodedPassword();

        User updated = userService.updateUser("u1", " Janet Doe ", " JANET@EXAMPLE.COM ", " 456 ");

        assertEquals("Janet Doe", updated.getFullName());
        assertEquals("janet@example.com", updated.getEmail());
        assertEquals("456", updated.getPhone());
        assertEquals(encodedPassword, updated.getEncodedPassword());
        assertNull(updated.getRole());
        assertEquals(AccountStatus.ACTIVE, updated.getAccountStatus());
        assertEquals(createdAt, updated.getCreatedAt());
    }

    @Test
    void userCreationFailsWithBlankEmail() {
        User user = User.withEncodedPassword("u1", "Jane Doe", " ", "123", "hash", null);
        assertThrows(BusinessRuleViolationException.class, () -> register(user));
    }

    @Test
    void userCreationFailsWithBlankFullName() {
        User user = User.withEncodedPassword("u1", " ", "jane@example.com", "123", "hash", null);
        assertThrows(BusinessRuleViolationException.class, () -> register(user));
    }

    @Test
    void userLookupMissingIdThrows() {
        assertThrows(ResourceNotFoundException.class, () -> userService.findById("missing"));
    }

    @Test
    void vehicleCreationSucceeds() {
        register(User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "hash", null));
        Vehicle vehicle = new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", "");
        assertEquals("v1", vehicleService.createVehicle(vehicle, "u1").getVehicleId());
    }

    @Test
    void vehicleLookupByUserReturnsSingleOwnedVehicle() {
        register(User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "hash", null));
        Vehicle vehicle = new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", "");

        vehicleService.createVehicle(vehicle, "u1");

        List<Vehicle> vehicles = vehicleService.findByUserId("u1");
        assertEquals(1, vehicles.size());
        assertEquals("v1", vehicles.getFirst().getVehicleId());
    }

    @Test
    void vehicleLookupByUserReturnsMultipleOwnedVehicles() {
        register(User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "hash", null));
        Vehicle first = new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", "");
        Vehicle second = new Vehicle("v2", "CA456", "SUV", "Honda", "CR-V", "Black", "");

        vehicleService.createVehicle(first, "u1");
        vehicleService.createVehicle(second, "u1");

        List<String> vehicleIds = vehicleService.findByUserId("u1").stream()
                .map(Vehicle::getVehicleId)
                .toList();
        assertEquals(2, vehicleIds.size());
        assertTrue(vehicleIds.contains("v1"));
        assertTrue(vehicleIds.contains("v2"));
    }

    @Test
    void vehicleLookupByUserExcludesOtherUsersVehicles() {
        register(User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "hash", null));
        register(User.withEncodedPassword("u2", "John Doe", "john@example.com", "456", "hash2", null));
        vehicleService.createVehicle(new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", ""), "u1");
        vehicleService.createVehicle(new Vehicle("v2", "CA456", "SUV", "Honda", "CR-V", "Black", ""), "u2");

        List<Vehicle> vehicles = vehicleService.findByUserId("u1");

        assertEquals(1, vehicles.size());
        assertEquals("v1", vehicles.getFirst().getVehicleId());
    }

    @Test
    void vehicleLookupByUserWithNoVehiclesReturnsEmptyList() {
        register(User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "hash", null));
        assertTrue(vehicleService.findByUserId("u1").isEmpty());
    }

    @Test
    void vehicleCreationFailsWithBlankPlate() {
        register(User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "hash", null));
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
    void findAllServicesReturnsUnfilteredCatalog() {
        Service activeService = new Service("active-service", "Premium Wash", "desc", BigDecimal.TEN, 30);
        Service inactiveService = new Service("inactive-service", "Basic Wash", "desc", BigDecimal.ONE, 15);
        inactiveService.deactivate();
        catalogService.createService(activeService);
        catalogService.createService(inactiveService);

        List<Service> services = catalogService.findAll();

        assertEquals(2, services.size());
        assertTrue(services.stream().anyMatch(service -> service.getServiceId().equals("active-service")));
        assertTrue(services.stream().anyMatch(service -> service.getServiceId().equals("inactive-service")));
    }

    @Test
    void findByActiveReturnsOnlyActiveServices() {
        Service activeService = new Service("active-service", "Premium Wash", "desc", BigDecimal.TEN, 30);
        Service inactiveService = new Service("inactive-service", "Basic Wash", "desc", BigDecimal.ONE, 15);
        inactiveService.deactivate();
        catalogService.createService(activeService);
        catalogService.createService(inactiveService);

        List<Service> services = catalogService.findByActive(true);

        assertEquals(1, services.size());
        assertEquals("active-service", services.getFirst().getServiceId());
    }

    @Test
    void findByActiveReturnsOnlyInactiveServices() {
        Service activeService = new Service("active-service", "Premium Wash", "desc", BigDecimal.TEN, 30);
        Service inactiveService = new Service("inactive-service", "Basic Wash", "desc", BigDecimal.ONE, 15);
        inactiveService.deactivate();
        catalogService.createService(activeService);
        catalogService.createService(inactiveService);

        List<Service> services = catalogService.findByActive(false);

        assertEquals(1, services.size());
        assertEquals("inactive-service", services.getFirst().getServiceId());
    }

    @Test
    void findByActiveReturnsEmptyListWhenNoServicesMatch() {
        Service activeService = new Service("active-service", "Premium Wash", "desc", BigDecimal.TEN, 30);
        catalogService.createService(activeService);

        assertTrue(catalogService.findByActive(false).isEmpty());
    }

    @Test
    void bookingCreationSucceeds() {
        User user = User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "hash", null);
        Vehicle vehicle = new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", "");
        Service service = new Service("s1", "Premium Wash", "desc", BigDecimal.TEN, 30);
        register(user);
        vehicleService.createVehicle(vehicle, "u1");
        catalogService.createService(service);

        Booking booking = new Booking("b1", user, vehicle, service, LocalDateTime.now().plusDays(1), "none");
        assertEquals("b1", bookingService.createBooking(booking).getBookingId());
    }

    @Test
    void bookingCreationFailsWhenPast() {
        User user = User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "hash", null);
        Vehicle vehicle = new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", "");
        Service service = new Service("s1", "Premium Wash", "desc", BigDecimal.TEN, 30);
        register(user);
        vehicleService.createVehicle(vehicle, "u1");
        catalogService.createService(service);

        Booking booking = new Booking("b1", user, vehicle, service, LocalDateTime.now().minusHours(1), "none");
        assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBooking_shouldRejectUnknownUser() {
        Vehicle vehicle = new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", "");
        Service service = new Service("s1", "Premium Wash", "desc", BigDecimal.TEN, 30);
        catalogService.createService(service);

        Booking booking = new Booking("b1", User.withEncodedPassword("missing", "Missing", "missing@example.com", "123", "hash", null), vehicle, service, LocalDateTime.now().plusDays(1), "none");

        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBooking_shouldRejectUnknownVehicle() {
        User user = User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "hash", null);
        Service service = new Service("s1", "Premium Wash", "desc", BigDecimal.TEN, 30);
        register(user);
        catalogService.createService(service);

        Booking booking = new Booking("b1", user, new Vehicle("missing", "CA123", "Sedan", "Toyota", "Corolla", "Blue", ""), service, LocalDateTime.now().plusDays(1), "none");

        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBooking_shouldRejectUnknownService() {
        User user = User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "hash", null);
        Vehicle vehicle = new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", "");
        register(user);
        vehicleService.createVehicle(vehicle, "u1");

        Booking booking = new Booking("b1", user, vehicle, new Service("missing", "Missing", "desc", BigDecimal.TEN, 30), LocalDateTime.now().plusDays(1), "none");

        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBooking_shouldRejectInactiveService() {
        User user = User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "hash", null);
        Vehicle vehicle = new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", "");
        Service service = new Service("s1", "Premium Wash", "desc", BigDecimal.TEN, 30);
        service.deactivate();
        register(user);
        vehicleService.createVehicle(vehicle, "u1");
        catalogService.createService(service);

        Booking booking = new Booking("b1", user, vehicle, service, LocalDateTime.now().plusDays(1), "none");

        assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBooking_shouldRejectVehicleOwnedByDifferentUser() {
        User firstUser = User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "hash", null);
        User secondUser = User.withEncodedPassword("u2", "John Doe", "john@example.com", "456", "hash", null);
        Vehicle vehicle = new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", "");
        Service service = new Service("s1", "Premium Wash", "desc", BigDecimal.TEN, 30);
        register(firstUser);
        register(secondUser);
        vehicleService.createVehicle(vehicle, "u2");
        catalogService.createService(service);

        Booking booking = new Booking("b1", firstUser, vehicle, service, LocalDateTime.now().plusDays(1), "none");

        assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBooking_shouldRejectPastScheduledDateTime() {
        Booking booking = newBookingWithFixture("past-slot", LocalDateTime.now().minusHours(1));

        assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBooking_shouldRejectFullTimeSlot() {
        LocalDateTime scheduledDateTime = LocalDateTime.now().plusDays(3).withNano(0);
        bookingService.createBooking(newBookingWithFixture("full-slot-a", scheduledDateTime));

        Booking overlappingBooking = newBookingWithFixture("full-slot-b", scheduledDateTime);

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(overlappingBooking));
        assertTrue(exception.getMessage().contains("time slot"));
    }

    @Test
    void createBooking_shouldIgnoreCancelledBookingWhenCheckingSlotCapacity() {
        LocalDateTime scheduledDateTime = LocalDateTime.now().plusDays(4).withNano(0);
        Booking cancelledBooking = bookingService.createBooking(newBookingWithFixture("cancelled-slot-a", scheduledDateTime));
        bookingService.cancelBooking(cancelledBooking.getBookingId(), "cancelled-slot-a-user");

        Booking replacementBooking = newBookingWithFixture("cancelled-slot-b", scheduledDateTime);

        assertEquals("cancelled-slot-b-booking", bookingService.createBooking(replacementBooking).getBookingId());
    }

    @Test
    void createBooking_shouldRejectSameVehicleConflictAtSameDateTime() {
        LocalDateTime scheduledDateTime = LocalDateTime.now().plusDays(5).withNano(0);
        Booking existingBooking = newBookingWithFixture("vehicle-conflict", scheduledDateTime);
        bookingService.createBooking(existingBooking);

        Booking conflictingBooking = new Booking("vehicle-conflict-booking-2", existingBooking.getUser(), existingBooking.getVehicle(), existingBooking.getService(), scheduledDateTime, "conflict");

        BusinessRuleViolationException exception = assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(conflictingBooking));
        assertTrue(exception.getMessage().contains("Customer vehicle"));
    }

    @Test
    void createBooking_shouldStillAllowValidFutureBooking() {
        LocalDateTime scheduledDateTime = LocalDateTime.now().plusDays(6).withNano(0);
        Booking booking = newBookingWithFixture("valid-future-slot", scheduledDateTime);

        assertEquals("valid-future-slot-booking", bookingService.createBooking(booking).getBookingId());
    }

    @Test
    void bookingConfirmationSucceeds() {
        Booking booking = createSavedBooking();
        assertEquals(BookingStatus.CONFIRMED, bookingService.confirmBooking(booking.getBookingId()).getStatus());
    }

    @Test
    void cancelledBookingCannotBeConfirmed() {
        Booking booking = createSavedBooking();
        bookingService.cancelBooking(booking.getBookingId(), "u1");
        assertThrows(BusinessRuleViolationException.class, () -> bookingService.confirmBooking(booking.getBookingId()));
    }

    @Test
    void customerCanCancelOwnFutureBooking() {
        Booking booking = createSavedBooking();

        Booking cancelled = bookingService.cancelBooking(booking.getBookingId(), "u1");

        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
        assertEquals(BookingStatus.CANCELLED, bookingService.findById(booking.getBookingId()).getStatus());
    }

    @Test
    void customerCannotCancelAnotherCustomersBooking() {
        Booking booking = createSavedBooking();

        assertThrows(BusinessRuleViolationException.class, () -> bookingService.cancelBooking(booking.getBookingId(), "u2"));
        assertEquals(BookingStatus.CREATED, bookingService.findById(booking.getBookingId()).getStatus());
    }

    @Test
    void customerCannotCancelPastBooking() {
        Booking booking = createSavedBooking();
        booking.setScheduledDateTime(LocalDateTime.now().minusHours(1));

        assertThrows(BusinessRuleViolationException.class, () -> bookingService.cancelBooking(booking.getBookingId(), "u1"));
        assertEquals(BookingStatus.CREATED, bookingService.findById(booking.getBookingId()).getStatus());
    }

    @Test
    void completedBookingCannotBeCancelled() {
        Booking booking = createSavedBooking();
        assertTrue(booking.confirm());
        assertTrue(booking.startService());
        assertTrue(booking.completeService());

        assertThrows(BusinessRuleViolationException.class, () -> bookingService.cancelBooking(booking.getBookingId(), "u1"));
        assertEquals(BookingStatus.COMPLETED, bookingService.findById(booking.getBookingId()).getStatus());
    }

    @Test
    void alreadyCancelledBookingCannotBeCancelledAgain() {
        Booking booking = createSavedBooking();
        bookingService.cancelBooking(booking.getBookingId(), "u1");

        assertThrows(BusinessRuleViolationException.class, () -> bookingService.cancelBooking(booking.getBookingId(), "u1"));
        assertEquals(BookingStatus.CANCELLED, bookingService.findById(booking.getBookingId()).getStatus());
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

        queueService.callNext(queueEntry.getQueueEntryId());
        queueService.startService(queueEntry.getQueueEntryId());
        assertNotNull(queueService.completeQueueEntry(queueEntry.getQueueEntryId()).getCompletedAt());
    }

    @Test
    void createQueueEntry_shouldRejectUnknownBooking() {
        Service service = new Service("s1", "Premium Wash", "desc", BigDecimal.TEN, 30);
        catalogService.createService(service);
        Booking booking = new Booking("missing", User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "hash", null), new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", ""), service, LocalDateTime.now().plusDays(1), "none");

        assertThrows(ResourceNotFoundException.class, () -> queueService.createQueueEntry(new QueueEntry("q1", booking, service, 1)));
    }

    @Test
    void createQueueEntry_shouldRejectUnknownService() {
        Booking booking = createSavedBooking();

        assertThrows(ResourceNotFoundException.class, () -> queueService.createQueueEntry(new QueueEntry("q1", booking, new Service("missing", "Missing", "desc", BigDecimal.TEN, 30), 1)));
    }

    @Test
    void createQueueEntry_shouldRejectMismatchedBookingService() {
        Booking booking = createSavedBooking();
        Service otherService = new Service("s2", "Detail", "desc", BigDecimal.TEN, 30);
        catalogService.createService(otherService);

        assertThrows(BusinessRuleViolationException.class, () -> queueService.createQueueEntry(new QueueEntry("q1", booking, otherService, 1)));
    }

    @Test
    void queueWorkflow_shouldRejectStartBeforeCall() {
        QueueEntry queueEntry = createSavedQueueEntry();

        assertThrows(BusinessRuleViolationException.class, () -> queueService.startService(queueEntry.getQueueEntryId()));
        assertEquals(QueueStatus.WAITING, queueService.findById(queueEntry.getQueueEntryId()).getQueueStatus());
    }

    @Test
    void queueWorkflow_shouldRejectCallAlreadyCompleted() {
        QueueEntry queueEntry = createCompletedQueueEntry();

        assertThrows(BusinessRuleViolationException.class, () -> queueService.callNext(queueEntry.getQueueEntryId()));
    }

    @Test
    void queueWorkflow_shouldRejectStartAlreadyCompleted() {
        QueueEntry queueEntry = createCompletedQueueEntry();

        assertThrows(BusinessRuleViolationException.class, () -> queueService.startService(queueEntry.getQueueEntryId()));
    }

    @Test
    void queueWorkflow_shouldRejectCompleteAlreadyCompleted() {
        QueueEntry queueEntry = createCompletedQueueEntry();

        assertThrows(BusinessRuleViolationException.class, () -> queueService.completeQueueEntry(queueEntry.getQueueEntryId()));
    }

    @Test
    void missingQueueLookupThrows() {
        assertThrows(ResourceNotFoundException.class, () -> queueService.findById("missing"));
    }

    @Test
    void bookingConfirmationCreatesNotification() {
        Booking booking = createSavedBooking();

        bookingService.confirmBooking(booking.getBookingId());

        List<Notification> notifications = notificationService.findByUserId("u1");
        assertEquals(1, notifications.size());
        assertEquals("BOOKING_CONFIRMED", notifications.getFirst().getType());
        assertEquals("Your booking has been confirmed.", notifications.getFirst().getMessage());
    }

    @Test
    void bookingCancellationCreatesNotification() {
        Booking booking = createSavedBooking();

        bookingService.cancelBooking(booking.getBookingId(), "u1");

        List<Notification> notifications = notificationService.findByUserId("u1");
        assertEquals(1, notifications.size());
        assertEquals("BOOKING_CANCELLED", notifications.getFirst().getType());
        assertEquals("Your booking has been cancelled.", notifications.getFirst().getMessage());
    }

    @Test
    void queueCallCreatesNotification() {
        QueueEntry queueEntry = createSavedQueueEntry();

        queueService.callNext(queueEntry.getQueueEntryId());

        List<Notification> notifications = notificationService.findByUserId("u1");
        assertEquals(1, notifications.size());
        assertEquals("QUEUE_CALLED", notifications.getFirst().getType());
        assertEquals("Your vehicle is next in the queue.", notifications.getFirst().getMessage());
    }

    @Test
    void queueServiceStartCreatesNotification() {
        QueueEntry queueEntry = createSavedQueueEntry();

        queueService.callNext(queueEntry.getQueueEntryId());
        queueService.startService(queueEntry.getQueueEntryId());

        List<Notification> notifications = notificationService.findByUserId("u1");
        assertEquals(2, notifications.size());
        assertTrue(notifications.stream().anyMatch(notification -> notification.getType().equals("SERVICE_STARTED")
                && notification.getMessage().equals("Your service has started.")));
    }

    @Test
    void queueServiceCompletionCreatesNotification() {
        QueueEntry queueEntry = createSavedQueueEntry();
        queueService.callNext(queueEntry.getQueueEntryId());
        queueService.startService(queueEntry.getQueueEntryId());

        queueService.completeQueueEntry(queueEntry.getQueueEntryId());

        List<Notification> notifications = notificationService.findByUserId("u1");
        assertEquals(3, notifications.size());
        assertTrue(notifications.stream().anyMatch(notification -> notification.getType().equals("SERVICE_COMPLETED")
                && notification.getMessage().equals("Your service has been completed.")));
    }

    @Test
    void recentNotificationsCanBeRetrievedByUserId() {
        Booking booking = createSavedBooking();
        bookingService.confirmBooking(booking.getBookingId());
        bookingService.cancelBooking(booking.getBookingId(), "u1");

        List<Notification> notifications = notificationService.findRecentByUserId("u1");

        assertEquals(2, notifications.size());
        assertEquals("BOOKING_CANCELLED", notifications.getFirst().getType());
    }

    private QueueEntry createSavedQueueEntry() {
        Booking booking = createSavedBooking();
        return queueService.createQueueEntry(new QueueEntry("q1", booking, booking.getService(), 1));
    }

    private QueueEntry createCompletedQueueEntry() {
        QueueEntry queueEntry = createSavedQueueEntry();
        queueService.callNext(queueEntry.getQueueEntryId());
        queueService.startService(queueEntry.getQueueEntryId());
        return queueService.completeQueueEntry(queueEntry.getQueueEntryId());
    }

    private Booking createSavedBooking() {
        User user = User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "hash", null);
        Vehicle vehicle = new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", "");
        Service service = new Service("s1", "Premium Wash", "desc", BigDecimal.TEN, 30);
        register(user);
        vehicleService.createVehicle(vehicle, "u1");
        catalogService.createService(service);
        Booking booking = new Booking("b1", user, vehicle, service, LocalDateTime.now().plusDays(1), "none");
        return bookingService.createBooking(booking);
    }

    @Test
    void dailySummaryReturnsZeroTotalsWhenNoDataExists() {
        DailySummaryReportResponse report = dailySummaryReportService.generateDailySummary(LocalDateTime.now().plusDays(10).toLocalDate());

        assertEquals(0, report.totalBookings());
        assertEquals(0, report.totalQueueEntries());
        assertEquals(0, report.pendingWorkload());
    }

    @Test
    void dailySummaryCountsBookingsForSelectedDateOnly() {
        LocalDateTime reportDateTime = LocalDateTime.now().plusDays(10);
        createSavedBooking("report-selected", reportDateTime, BookingStatus.CREATED);
        createSavedBooking("report-other", reportDateTime.plusDays(1), BookingStatus.CREATED);

        DailySummaryReportResponse report = dailySummaryReportService.generateDailySummary(reportDateTime.toLocalDate());

        assertEquals(1, report.totalBookings());
    }

    @Test
    void dailySummaryCountsConfirmedBookings() {
        LocalDateTime reportDateTime = LocalDateTime.now().plusDays(10);
        createSavedBooking("report-confirmed", reportDateTime, BookingStatus.CONFIRMED);
        createSavedBooking("report-created", reportDateTime.plusMinutes(1), BookingStatus.CREATED);

        DailySummaryReportResponse report = dailySummaryReportService.generateDailySummary(reportDateTime.toLocalDate());

        assertEquals(2, report.totalBookings());
        assertEquals(1, report.confirmedBookings());
    }

    @Test
    void dailySummaryCountsCancelledBookings() {
        LocalDateTime reportDateTime = LocalDateTime.now().plusDays(10);
        createSavedBooking("report-cancelled", reportDateTime, BookingStatus.CANCELLED);
        createSavedBooking("report-active", reportDateTime.plusMinutes(1), BookingStatus.CONFIRMED);

        DailySummaryReportResponse report = dailySummaryReportService.generateDailySummary(reportDateTime.toLocalDate());

        assertEquals(1, report.cancelledBookings());
    }

    @Test
    void dailySummaryExcludesCancelledBookingsFromCompletedTotals() {
        LocalDateTime reportDateTime = LocalDateTime.now().plusDays(10);
        createSavedBooking("report-cancelled-complete", reportDateTime, BookingStatus.CANCELLED);
        createSavedBooking("report-complete", reportDateTime.plusMinutes(1), BookingStatus.COMPLETED);

        DailySummaryReportResponse report = dailySummaryReportService.generateDailySummary(reportDateTime.toLocalDate());

        assertEquals(1, report.completedBookings());
        assertEquals(1, report.cancelledBookings());
    }

    @Test
    void dailySummaryCountsQueueEntriesByStatus() {
        LocalDateTime reportDateTime = LocalDateTime.now().plusDays(10);
        createSavedQueueEntry("queue-waiting", reportDateTime, QueueStatus.WAITING);
        createSavedQueueEntry("queue-called", reportDateTime.plusMinutes(1), QueueStatus.CALLED);
        createSavedQueueEntry("queue-progress", reportDateTime.plusMinutes(2), QueueStatus.IN_PROGRESS);
        createSavedQueueEntry("queue-completed", reportDateTime.plusMinutes(3), QueueStatus.COMPLETED);

        DailySummaryReportResponse report = dailySummaryReportService.generateDailySummary(reportDateTime.toLocalDate());

        assertEquals(4, report.totalQueueEntries());
        assertEquals(1, report.waitingQueueEntries());
        assertEquals(1, report.calledQueueEntries());
        assertEquals(1, report.inProgressQueueEntries());
        assertEquals(1, report.completedQueueEntries());
    }

    @Test
    void dailySummaryCalculatesPendingWorkloadCorrectly() {
        LocalDateTime reportDateTime = LocalDateTime.now().plusDays(10);
        createSavedBooking("pending-created", reportDateTime, BookingStatus.CREATED);
        createSavedBooking("pending-confirmed", reportDateTime.plusMinutes(1), BookingStatus.CONFIRMED);
        createSavedBooking("pending-service", reportDateTime.plusMinutes(2), BookingStatus.IN_SERVICE);
        createSavedBooking("pending-cancelled", reportDateTime.plusMinutes(3), BookingStatus.CANCELLED);
        createSavedBooking("pending-completed", reportDateTime.plusMinutes(4), BookingStatus.COMPLETED);

        DailySummaryReportResponse report = dailySummaryReportService.generateDailySummary(reportDateTime.toLocalDate());

        assertEquals(3, report.pendingWorkload());
    }

    private Booking newBookingWithFixture(String prefix, LocalDateTime scheduledDateTime) {
        User user = User.withEncodedPassword(prefix + "-user", "Slot User", prefix + "@example.com", "123", "hash", null);
        Vehicle vehicle = new Vehicle(prefix + "-vehicle", prefix + "-plate", "Sedan", "Toyota", "Corolla", "Blue", "");
        Service service = new Service(prefix + "-service", "Slot Wash", "desc", BigDecimal.TEN, 30);
        register(user);
        vehicleService.createVehicle(vehicle, user.getUserId());
        catalogService.createService(service);
        return new Booking(prefix + "-booking", user, vehicle, service, scheduledDateTime, "none");
    }

    private void createSavedQueueEntry(String prefix, LocalDateTime scheduledDateTime, QueueStatus queueStatus) {
        Booking booking = createSavedBooking(prefix, scheduledDateTime, BookingStatus.CONFIRMED);
        QueueEntry queueEntry = queueService.createQueueEntry(new QueueEntry(prefix + "-queue", booking, booking.getService(), 1));
        queueEntry.setQueueStatus(queueStatus);
    }

    private Booking createSavedBooking(String prefix, LocalDateTime scheduledDateTime, BookingStatus bookingStatus) {
        User user = User.withEncodedPassword(prefix + "-user", "Report User", prefix + "@example.com", "123", "hash", null);
        Vehicle vehicle = new Vehicle(prefix + "-vehicle", prefix + "-plate", "Sedan", "Toyota", "Corolla", "Blue", "");
        Service service = new Service(prefix + "-service", "Premium Wash", "desc", BigDecimal.TEN, 30);
        register(user);
        vehicleService.createVehicle(vehicle, user.getUserId());
        catalogService.createService(service);
        Booking booking = new Booking(prefix + "-booking", user, vehicle, service, scheduledDateTime, "none");
        Booking savedBooking = bookingService.createBooking(booking);
        savedBooking.setStatus(bookingStatus);
        return savedBooking;
    }

    // ==================== DUPLICATE EMAIL HANDLING TESTS ====================

    @Test
    void testCreateUser_DuplicateEmail_ThrowsException() {
        // Create first user
        User user1 = User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "hash", null);
        register(user1);

        // Try to create second user with same email
        User user2 = User.withEncodedPassword("u2", "John Doe", "jane@example.com", "456", "hash2", null);

        assertThrows(BusinessRuleViolationException.class, () -> register(user2));
    }

    @Test
    void testUpdateUser_DuplicateEmail_ThrowsException() {
        // Create first user
        User user1 = User.withEncodedPassword("u1", "Jane Doe", "jane@example.com", "123", "hash", null);
        register(user1);

        // Create second user with different email
        User user2 = User.withEncodedPassword("u2", "John Doe", "john@example.com", "456", "hash2", null);
        register(user2);

        // Try to update user2 to user1's email
        user2.setEmail("jane@example.com");

        assertThrows(BusinessRuleViolationException.class, () -> userService.updateUser(user2));
    }

    @Test
    void testDuplicateEmailCaseInsensitive_ThrowsException() {
        // Create first user with mixed case email
        User user1 = User.withEncodedPassword("u1", "Jane Doe", "Test@Example.com", "123", "hash", null);
        register(user1);

        // Try to create second user with same email different case
        User user2 = User.withEncodedPassword("u2", "John Doe", "test@example.com", "456", "hash2", null);

        assertThrows(BusinessRuleViolationException.class, () -> register(user2));
    }

    private User register(User user) {
        return userService.createUser(new CreateUserCommand(user.getUserId(), user.getFullName(), user.getEmail(),
                user.getPhone(), "LocalTestPassword123!"));
    }

}
