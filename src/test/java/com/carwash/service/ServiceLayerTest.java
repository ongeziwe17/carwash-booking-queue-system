package com.carwash.service;

import com.carwash.api.dto.DailySummaryReportResponse;
import com.carwash.domain.*;
import com.carwash.enums.BookingStatus;
import com.carwash.enums.QueueStatus;
import com.carwash.repository.inmemory.*;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

        userService = new UserManagementService(userRepository);
        vehicleService = new VehicleManagementService(vehicleRepository, userRepository);
        catalogService = new ServiceCatalogService(serviceRepository);
        notificationService = new NotificationManagementService(notificationRepository);
        bookingService = new BookingManagementService(bookingRepository, userRepository, vehicleRepository, serviceRepository, notificationService);
        queueService = new QueueManagementService(queueRepository, bookingRepository, serviceRepository, notificationService);
        dailySummaryReportService = new DailySummaryReportService(bookingRepository, queueRepository);
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
    void vehicleLookupByUserReturnsSingleOwnedVehicle() {
        userService.createUser(new User("u1", "Jane Doe", "jane@example.com", "123", "hash", null));
        Vehicle vehicle = new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", "");

        vehicleService.createVehicle(vehicle, "u1");

        List<Vehicle> vehicles = vehicleService.findByUserId("u1");
        assertEquals(1, vehicles.size());
        assertEquals("v1", vehicles.getFirst().getVehicleId());
    }

    @Test
    void vehicleLookupByUserReturnsMultipleOwnedVehicles() {
        userService.createUser(new User("u1", "Jane Doe", "jane@example.com", "123", "hash", null));
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
        userService.createUser(new User("u1", "Jane Doe", "jane@example.com", "123", "hash", null));
        userService.createUser(new User("u2", "John Doe", "john@example.com", "456", "hash2", null));
        vehicleService.createVehicle(new Vehicle("v1", "CA123", "Sedan", "Toyota", "Corolla", "Blue", ""), "u1");
        vehicleService.createVehicle(new Vehicle("v2", "CA456", "SUV", "Honda", "CR-V", "Black", ""), "u2");

        List<Vehicle> vehicles = vehicleService.findByUserId("u1");

        assertEquals(1, vehicles.size());
        assertEquals("v1", vehicles.getFirst().getVehicleId());
    }

    @Test
    void vehicleLookupByUserWithNoVehiclesReturnsEmptyList() {
        userService.createUser(new User("u1", "Jane Doe", "jane@example.com", "123", "hash", null));
        assertTrue(vehicleService.findByUserId("u1").isEmpty());
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

        queueService.startService(queueEntry.getQueueEntryId());
        assertNotNull(queueService.completeQueueEntry(queueEntry.getQueueEntryId()).getCompletedAt());
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

        queueService.startService(queueEntry.getQueueEntryId());

        List<Notification> notifications = notificationService.findByUserId("u1");
        assertEquals(1, notifications.size());
        assertEquals("SERVICE_STARTED", notifications.getFirst().getType());
        assertEquals("Your service has started.", notifications.getFirst().getMessage());
    }

    @Test
    void queueServiceCompletionCreatesNotification() {
        QueueEntry queueEntry = createSavedQueueEntry();
        queueService.startService(queueEntry.getQueueEntryId());

        queueService.completeQueueEntry(queueEntry.getQueueEntryId());

        List<Notification> notifications = notificationService.findByUserId("u1");
        assertEquals(2, notifications.size());
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
        createSavedBooking("report-created", reportDateTime, BookingStatus.CREATED);

        DailySummaryReportResponse report = dailySummaryReportService.generateDailySummary(reportDateTime.toLocalDate());

        assertEquals(2, report.totalBookings());
        assertEquals(1, report.confirmedBookings());
    }

    @Test
    void dailySummaryCountsCancelledBookings() {
        LocalDateTime reportDateTime = LocalDateTime.now().plusDays(10);
        createSavedBooking("report-cancelled", reportDateTime, BookingStatus.CANCELLED);
        createSavedBooking("report-active", reportDateTime, BookingStatus.CONFIRMED);

        DailySummaryReportResponse report = dailySummaryReportService.generateDailySummary(reportDateTime.toLocalDate());

        assertEquals(1, report.cancelledBookings());
    }

    @Test
    void dailySummaryExcludesCancelledBookingsFromCompletedTotals() {
        LocalDateTime reportDateTime = LocalDateTime.now().plusDays(10);
        createSavedBooking("report-cancelled-complete", reportDateTime, BookingStatus.CANCELLED);
        createSavedBooking("report-complete", reportDateTime, BookingStatus.COMPLETED);

        DailySummaryReportResponse report = dailySummaryReportService.generateDailySummary(reportDateTime.toLocalDate());

        assertEquals(1, report.completedBookings());
        assertEquals(1, report.cancelledBookings());
    }

    @Test
    void dailySummaryCountsQueueEntriesByStatus() {
        LocalDateTime reportDateTime = LocalDateTime.now().plusDays(10);
        createSavedQueueEntry("queue-waiting", reportDateTime, QueueStatus.WAITING);
        createSavedQueueEntry("queue-called", reportDateTime, QueueStatus.CALLED);
        createSavedQueueEntry("queue-progress", reportDateTime, QueueStatus.IN_PROGRESS);
        createSavedQueueEntry("queue-completed", reportDateTime, QueueStatus.COMPLETED);

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
        createSavedBooking("pending-confirmed", reportDateTime, BookingStatus.CONFIRMED);
        createSavedBooking("pending-service", reportDateTime, BookingStatus.IN_SERVICE);
        createSavedBooking("pending-cancelled", reportDateTime, BookingStatus.CANCELLED);
        createSavedBooking("pending-completed", reportDateTime, BookingStatus.COMPLETED);

        DailySummaryReportResponse report = dailySummaryReportService.generateDailySummary(reportDateTime.toLocalDate());

        assertEquals(3, report.pendingWorkload());
    }

    private void createSavedQueueEntry(String prefix, LocalDateTime scheduledDateTime, QueueStatus queueStatus) {
        Booking booking = createSavedBooking(prefix, scheduledDateTime, BookingStatus.CONFIRMED);
        QueueEntry queueEntry = queueService.createQueueEntry(new QueueEntry(prefix + "-queue", booking, booking.getService(), 1));
        queueEntry.setQueueStatus(queueStatus);
    }

    private Booking createSavedBooking(String prefix, LocalDateTime scheduledDateTime, BookingStatus bookingStatus) {
        User user = new User(prefix + "-user", "Report User", prefix + "@example.com", "123", "hash", null);
        Vehicle vehicle = new Vehicle(prefix + "-vehicle", prefix + "-plate", "Sedan", "Toyota", "Corolla", "Blue", "");
        Service service = new Service(prefix + "-service", "Premium Wash", "desc", BigDecimal.TEN, 30);
        userService.createUser(user);
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