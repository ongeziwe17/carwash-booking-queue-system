package com.carwash.service;

import com.carwash.config.BookingPolicyProperties;
import com.carwash.config.NotificationPolicyProperties;
import com.carwash.config.PasswordSecurityProperties;
import com.carwash.config.QueuePolicyProperties;
import com.carwash.domain.Booking;
import com.carwash.domain.QueueEntry;
import com.carwash.domain.User;
import com.carwash.domain.Vehicle;
import com.carwash.enums.BookingStatus;
import com.carwash.enums.QueueStatus;
import com.carwash.repository.inmemory.InMemoryBookingRepository;
import com.carwash.repository.inmemory.InMemoryDataCoordinator;
import com.carwash.repository.inmemory.InMemoryNotificationRepository;
import com.carwash.repository.inmemory.InMemoryQueueEntryRepository;
import com.carwash.repository.inmemory.InMemoryServiceRepository;
import com.carwash.repository.inmemory.InMemoryUserRepository;
import com.carwash.repository.inmemory.InMemoryVehicleRepository;
import com.carwash.security.UserCredentialService;
import com.carwash.service.command.CreateUserCommand;
import com.carwash.testsupport.DeterministicTestNotificationIdGenerator;
import com.carwash.testsupport.TestDates;
import com.carwash.testsupport.TestIdFactory;
import com.carwash.testsupport.UserFixtureBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

abstract class ServiceTestSupport {

    protected InMemoryUserRepository userRepository;
    protected InMemoryVehicleRepository vehicleRepository;
    protected InMemoryServiceRepository serviceRepository;
    protected InMemoryBookingRepository bookingRepository;
    protected InMemoryQueueEntryRepository queueRepository;
    protected InMemoryNotificationRepository notificationRepository;
    protected InMemoryDataCoordinator coordinator;

    protected UserManagementService userService;
    protected VehicleManagementService vehicleService;
    protected ServiceCatalogService catalogService;
    protected BookingManagementService bookingService;
    protected QueueManagementService queueService;
    protected QueueOrderingService queueOrdering;
    protected NotificationManagementService notificationService;
    protected DailySummaryReportService reportService;
    protected DeterministicTestNotificationIdGenerator notificationIds;
    protected TestIdFactory ids;
    protected Clock clock;

    @BeforeEach
    final void createFreshServiceGraph(TestInfo testInfo) {
        String className = testInfo.getTestClass().map(Class::getSimpleName).orElse("service");
        String methodName = testInfo.getTestMethod().map(method -> method.getName()).orElse("test");
        ids = new TestIdFactory(className + "-" + methodName);

        userRepository = new InMemoryUserRepository();
        vehicleRepository = new InMemoryVehicleRepository();
        serviceRepository = new InMemoryServiceRepository();
        bookingRepository = new InMemoryBookingRepository();
        queueRepository = new InMemoryQueueEntryRepository();
        notificationRepository = new InMemoryNotificationRepository();
        coordinator = new InMemoryDataCoordinator();
        notificationIds = new DeterministicTestNotificationIdGenerator();
        clock = Clock.fixed(Instant.parse("2089-01-15T12:00:00Z"), ZoneOffset.UTC);

        UserCredentialService credentialService = new UserCredentialService(
                new BCryptPasswordEncoder(4), new PasswordSecurityProperties(4, 12, 72));
        userService = new UserManagementService(userRepository, credentialService,
                vehicleRepository, bookingRepository, notificationRepository, coordinator);
        vehicleService = new VehicleManagementService(vehicleRepository, userRepository, bookingRepository, coordinator);
        queueOrdering = new QueueOrderingService(
                queueRepository, coordinator, new QueuePolicyProperties(Duration.ofMinutes(10)));
        catalogService = new ServiceCatalogService(
                serviceRepository, bookingRepository, queueRepository, coordinator, queueOrdering);
        notificationService = new NotificationManagementService(
                notificationRepository, userRepository, bookingRepository, coordinator, notificationIds,
                new NotificationPolicyProperties(10), clock);
        bookingService = new BookingManagementService(
                bookingRepository, userRepository, vehicleRepository, serviceRepository,
                queueRepository, notificationRepository, notificationService, coordinator,
                new BookingPolicyProperties(1, Duration.ZERO), clock);
        queueService = new QueueManagementService(
                queueRepository, bookingRepository, serviceRepository, notificationService, coordinator,
                queueOrdering, clock);
        reportService = new DailySummaryReportService(bookingRepository, queueRepository, coordinator);
    }

    protected User registerUser() {
        String id = ids.user();
        return register(id, ids.emailFor(id), "Integration User", "0821234567");
    }

    protected User register(String id, String email) {
        return register(id, email, "Integration User", "0821234567");
    }

    protected User register(String id, String email, String fullName, String phone) {
        return userService.createUser(new CreateUserCommand(
                id, fullName, email, phone, UserFixtureBuilder.DEFAULT_PASSWORD));
    }

    protected Vehicle createVehicle(User owner) {
        return vehicleService.createVehicle(new Vehicle(
                ids.vehicle(), ids.plate(), "SUV", "Toyota", "Rav4", "Black", ""), owner.getUserId());
    }

    protected com.carwash.domain.Service createService() {
        return catalogService.createService(new com.carwash.domain.Service(
                ids.service(), "Premium Wash", "integration test", BigDecimal.TEN, 30));
    }

    protected Booking newBookingWithFixture(LocalDateTime scheduledDateTime) {
        User user = registerUser();
        Vehicle vehicle = createVehicle(user);
        com.carwash.domain.Service service = createService();
        return new Booking(ids.booking(), user, vehicle, service, scheduledDateTime, "none");
    }

    protected Booking createSavedBooking() {
        return bookingService.createBooking(newBookingWithFixture(TestDates.future()));
    }

    protected Booking createConfirmedBooking() {
        Booking booking = createSavedBooking();
        return bookingService.confirmBooking(booking.getBookingId());
    }

    protected Booking createConfirmedBooking(LocalDateTime scheduledDateTime) {
        Booking booking = bookingService.createBooking(newBookingWithFixture(scheduledDateTime));
        return bookingService.confirmBooking(booking.getBookingId());
    }

    protected Booking createSavedBooking(LocalDateTime scheduledDateTime, BookingStatus status) {
        Booking booking = bookingService.createBooking(newBookingWithFixture(scheduledDateTime));
        booking.setStatus(status);
        return booking;
    }

    protected QueueEntry createSavedQueueEntry() {
        Booking booking = createConfirmedBooking();
        return queueService.createQueueEntry(new QueueEntry(ids.queueEntry(), booking, booking.getService()));
    }

    protected QueueEntry createSavedQueueEntry(LocalDateTime scheduledDateTime, QueueStatus status) {
        Booking booking = createConfirmedBooking(scheduledDateTime);
        QueueEntry queueEntry = queueService.createQueueEntry(
                new QueueEntry(ids.queueEntry(), booking, booking.getService()));
        queueEntry.setQueueStatus(status);
        if (!status.isActive()) queueEntry.updateQueueMetrics(queueEntry.getPosition(), 0);
        queueRepository.update(queueEntry);
        return queueEntry;
    }
}
