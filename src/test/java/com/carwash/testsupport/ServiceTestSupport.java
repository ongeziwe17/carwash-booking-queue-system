package com.carwash.testsupport;

import com.carwash.booking.application.AvailabilityService;
import com.carwash.booking.application.BookingManagementService;
import com.carwash.booking.application.BookingSlotPolicyService;
import com.carwash.catalog.application.ServiceCatalogService;
import com.carwash.identity.application.UserManagementService;
import com.carwash.notification.application.NotificationManagementService;
import com.carwash.queue.application.QueueManagementService;
import com.carwash.queue.application.QueueOrderingService;
import com.carwash.reporting.application.DailySummaryReportService;
import com.carwash.vehicle.application.VehicleManagementService;

import com.carwash.booking.application.BookingPolicyProperties;
import com.carwash.notification.application.NotificationPolicyProperties;
import com.carwash.access.infrastructure.PasswordSecurityProperties;
import com.carwash.queue.application.QueuePolicyProperties;
import com.carwash.booking.domain.Booking;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.identity.domain.User;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.queue.domain.QueueStatus;
import com.carwash.booking.infrastructure.InMemoryBookingRepository;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import com.carwash.notification.infrastructure.InMemoryNotificationRepository;
import com.carwash.queue.infrastructure.InMemoryQueueEntryRepository;
import com.carwash.catalog.infrastructure.InMemoryServiceRepository;
import com.carwash.catalog.infrastructure.InMemoryServiceOfferingRepository;
import com.carwash.identity.infrastructure.InMemoryUserRepository;
import com.carwash.vehicle.infrastructure.InMemoryVehicleRepository;
import com.carwash.access.application.UserCredentialService;
import com.carwash.identity.application.CreateUserCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public abstract class ServiceTestSupport {

    protected InMemoryUserRepository userRepository;
    protected InMemoryVehicleRepository vehicleRepository;
    protected InMemoryServiceRepository serviceRepository;
    protected InMemoryServiceOfferingRepository serviceOfferingRepository;
    protected InMemoryBookingRepository bookingRepository;
    protected InMemoryQueueEntryRepository queueRepository;
    protected InMemoryNotificationRepository notificationRepository;
    protected InMemoryDataCoordinator coordinator;

    protected UserManagementService userService;
    protected VehicleManagementService vehicleService;
    protected ServiceCatalogService catalogService;
    protected BookingManagementService bookingService;
    protected BookingSlotPolicyService bookingSlotPolicy;
    protected AvailabilityService availabilityService;
    protected QueueManagementService queueService;
    protected QueueOrderingService queueOrdering;
    protected NotificationManagementService notificationService;
    protected DailySummaryReportService reportService;
    protected DeterministicTestNotificationIdGenerator notificationIds;
    protected TestIdFactory ids;
    protected Clock clock;
    protected BookingPolicyProperties bookingPolicy;

    @BeforeEach
    protected final void createFreshServiceGraph(TestInfo testInfo) {
        String className = testInfo.getTestClass().map(Class::getSimpleName).orElse("service");
        String methodName = testInfo.getTestMethod().map(method -> method.getName()).orElse("test");
        ids = new TestIdFactory(className + "-" + methodName);

        userRepository = new InMemoryUserRepository();
        vehicleRepository = new InMemoryVehicleRepository();
        serviceRepository = new InMemoryServiceRepository();
        serviceOfferingRepository = new InMemoryServiceOfferingRepository();
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
                serviceRepository, serviceOfferingRepository, bookingRepository, queueRepository, coordinator, queueOrdering);
        notificationService = new NotificationManagementService(
                notificationRepository, userRepository, bookingRepository, coordinator, notificationIds,
                new NotificationPolicyProperties(10), clock);
        bookingPolicy = new BookingPolicyProperties(1, Duration.ZERO);
        bookingSlotPolicy = new BookingSlotPolicyService(bookingRepository, bookingPolicy, clock);
        availabilityService = new AvailabilityService(
                serviceRepository, bookingSlotPolicy, bookingPolicy, coordinator);
        bookingService = new BookingManagementService(
                bookingRepository, userRepository, vehicleRepository, serviceRepository,
                queueRepository, notificationRepository, notificationService, queueOrdering, coordinator,
                bookingPolicy, bookingSlotPolicy, clock);
        queueService = new QueueManagementService(
                queueRepository, bookingRepository, serviceRepository, notificationService, coordinator,
                queueOrdering, clock);
        reportService = new DailySummaryReportService(bookingService, queueService, coordinator);
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

    protected com.carwash.catalog.domain.Service createService() {
        return catalogService.createService(new com.carwash.catalog.domain.Service(
                ids.service(), "Premium Wash", "integration test", BigDecimal.TEN, 30));
    }

    protected Booking newBookingWithFixture(LocalDateTime scheduledDateTime) {
        User user = registerUser();
        Vehicle vehicle = createVehicle(user);
        com.carwash.catalog.domain.Service service = createService();
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
        QueueEntry created = queueService.createQueueEntry(
                new QueueEntry(ids.queueEntry(), booking, booking.getService()));
        return queueRepository.findById(created.getQueueEntryId()).orElseThrow();
    }

    protected QueueEntry createSavedQueueEntry(LocalDateTime scheduledDateTime, QueueStatus status) {
        Booking booking = createConfirmedBooking(scheduledDateTime);
        QueueEntry created = queueService.createQueueEntry(
                new QueueEntry(ids.queueEntry(), booking, booking.getService()));
        QueueEntry queueEntry = queueRepository.findById(created.getQueueEntryId()).orElseThrow();
        queueEntry.setQueueStatus(status);
        if (!status.isActive()) queueEntry.updateQueueMetrics(queueEntry.getPosition(), 0);
        queueRepository.update(queueEntry);
        return queueEntry;
    }
}
