package com.carwash.testsupport;

import com.carwash.booking.application.AvailabilityService;
import com.carwash.booking.application.BookingManagementService;
import com.carwash.booking.application.BranchAvailabilityDecisionService;
import com.carwash.booking.application.BookingSlotPolicyService;
import com.carwash.catalog.application.ServiceCatalogService;
import com.carwash.catalog.application.CreateServiceOfferingCommand;
import com.carwash.catalog.application.ServiceDefinitionUsageQuery;
import com.carwash.catalog.application.ServiceOfferingService;
import com.carwash.marketplace.application.CreateBranchCommand;
import com.carwash.marketplace.application.BranchSchedulingService;
import com.carwash.marketplace.application.ReplaceOperatingScheduleCommand;
import com.carwash.marketplace.application.WeeklyOperatingIntervalCommand;
import com.carwash.marketplace.application.MarketplaceManagementService;
import com.carwash.marketplace.application.RegisterBusinessCommand;
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
import com.carwash.marketplace.infrastructure.InMemoryCarWashBranchRepository;
import com.carwash.marketplace.infrastructure.InMemoryCarWashBusinessRepository;
import com.carwash.marketplace.infrastructure.InMemoryBranchOperatingScheduleRepository;
import com.carwash.marketplace.infrastructure.InMemoryTemporaryBranchClosureRepository;
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
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;

public abstract class ServiceTestSupport {

    protected InMemoryUserRepository userRepository;
    protected InMemoryVehicleRepository vehicleRepository;
    protected InMemoryServiceRepository serviceRepository;
    protected InMemoryServiceOfferingRepository serviceOfferingRepository;
    protected InMemoryCarWashBusinessRepository businessRepository;
    protected InMemoryCarWashBranchRepository branchRepository;
    protected InMemoryBranchOperatingScheduleRepository scheduleRepository;
    protected InMemoryTemporaryBranchClosureRepository closureRepository;
    protected InMemoryBookingRepository bookingRepository;
    protected InMemoryQueueEntryRepository queueRepository;
    protected InMemoryNotificationRepository notificationRepository;
    protected InMemoryDataCoordinator coordinator;

    protected UserManagementService userService;
    protected VehicleManagementService vehicleService;
    protected ServiceCatalogService catalogService;
    protected MarketplaceManagementService marketplaceService;
    protected BranchSchedulingService branchSchedulingService;
    protected ServiceOfferingService serviceOfferingService;
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
    protected String defaultBusinessId;
    protected String defaultBranchId;

    @BeforeEach
    protected final void createFreshServiceGraph(TestInfo testInfo) {
        String className = testInfo.getTestClass().map(Class::getSimpleName).orElse("service");
        String methodName = testInfo.getTestMethod().map(method -> method.getName()).orElse("test");
        ids = new TestIdFactory(className + "-" + methodName);

        userRepository = new InMemoryUserRepository();
        vehicleRepository = new InMemoryVehicleRepository();
        serviceRepository = new InMemoryServiceRepository();
        serviceOfferingRepository = new InMemoryServiceOfferingRepository();
        businessRepository = new InMemoryCarWashBusinessRepository();
        branchRepository = new InMemoryCarWashBranchRepository();
        scheduleRepository = new InMemoryBranchOperatingScheduleRepository();
        closureRepository = new InMemoryTemporaryBranchClosureRepository();
        coordinator = new InMemoryDataCoordinator();
        notificationIds = new DeterministicTestNotificationIdGenerator();
        clock = Clock.fixed(Instant.parse("2089-01-15T12:00:00Z"), ZoneOffset.UTC);
        marketplaceService = new MarketplaceManagementService(
                businessRepository, branchRepository, coordinator, clock);
        bookingRepository = new InMemoryBookingRepository(marketplaceService);
        queueRepository = new InMemoryQueueEntryRepository(marketplaceService);
        notificationRepository = new InMemoryNotificationRepository(marketplaceService);

        UserCredentialService credentialService = new UserCredentialService(
                new BCryptPasswordEncoder(4), new PasswordSecurityProperties(4, 12, 72));
        userService = new UserManagementService(userRepository, credentialService,
                vehicleRepository, bookingRepository, notificationRepository, coordinator);
        vehicleService = new VehicleManagementService(vehicleRepository, userRepository, bookingRepository, coordinator);
        branchSchedulingService = new BranchSchedulingService(
                businessRepository, branchRepository, scheduleRepository, closureRepository, coordinator, clock);
        ServiceDefinitionUsageQuery serviceUsage = new ServiceDefinitionUsageQuery() {
            @Override
            public boolean referencedByBooking(String serviceId) {
                return bookingRepository.existsByServiceId(serviceId);
            }

            @Override
            public boolean referencedByQueue(String serviceId) {
                return queueRepository.existsByServiceId(serviceId);
            }
        };
        catalogService = new ServiceCatalogService(
                serviceRepository, serviceOfferingRepository, serviceUsage, coordinator);
        serviceOfferingService = new ServiceOfferingService(
                serviceOfferingRepository, serviceRepository, marketplaceService, coordinator, clock);
        queueOrdering = new QueueOrderingService(
                queueRepository, coordinator, new QueuePolicyProperties(Duration.ofMinutes(10)), serviceOfferingService);
        notificationService = new NotificationManagementService(
                notificationRepository, userRepository, bookingRepository, coordinator, notificationIds,
                new NotificationPolicyProperties(10), clock);
        bookingPolicy = new BookingPolicyProperties(1, Duration.ZERO);
        bookingSlotPolicy = new BookingSlotPolicyService(bookingRepository, bookingPolicy, clock);
        BranchAvailabilityDecisionService branchAvailability = new BranchAvailabilityDecisionService(
                bookingRepository, marketplaceService, branchSchedulingService, serviceOfferingService,
                catalogService, bookingPolicy, clock);
        availabilityService = new AvailabilityService(
                serviceRepository, bookingSlotPolicy, bookingPolicy, coordinator);
        bookingService = new BookingManagementService(
                bookingRepository, userRepository, vehicleRepository, catalogService,
                serviceOfferingService, marketplaceService,
                queueRepository, notificationRepository, notificationService, queueOrdering, coordinator,
                bookingPolicy, bookingSlotPolicy, branchAvailability, clock);
        queueService = new QueueManagementService(
                queueRepository, bookingRepository, serviceOfferingService, marketplaceService,
                notificationService, coordinator,
                queueOrdering, clock);
        reportService = new DailySummaryReportService(bookingService, queueService, marketplaceService, coordinator);
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
        return vehicleService.createVehicle(
                TestAccess.platformAdministrator(), owner.getUserId(), ids.vehicle(), ids.plate(),
                "SUV", "Toyota", "Rav4", "Black", "");
    }

    protected com.carwash.catalog.domain.Service createService() {
        return catalogService.createService(new com.carwash.catalog.domain.Service(
                ids.service(), "Premium Wash", "integration test", BigDecimal.TEN, 30));
    }

    protected String createOffering(com.carwash.catalog.domain.Service service) {
        String offeringId = ids.offering();
        serviceOfferingService.createOffering(
                TestAccess.platformAdministrator(), ensureDefaultBranch(), new CreateServiceOfferingCommand(
                offeringId, service.getServiceId(), service.getPrice(), service.getEstimatedDurationMin(),
                bookingPolicy.maxActiveBookingsPerSlot()));
        return offeringId;
    }

    protected Booking newBookingWithFixture(LocalDateTime scheduledDateTime) {
        User user = registerUser();
        Vehicle vehicle = createVehicle(user);
        com.carwash.catalog.domain.Service service = createService();
        String branchId = ensureDefaultBranch();
        String offeringId = createOffering(service);
        return new Booking(
                ids.booking(), user, vehicle, branchId, offeringId, service, scheduledDateTime, "none");
    }

    protected String ensureDefaultBranch() {
        if (defaultBranchId != null) return defaultBranchId;
        defaultBusinessId = ids.business();
        marketplaceService.registerBusiness(TestAccess.platformAdministrator(), new RegisterBusinessCommand(
                defaultBusinessId, "Test Car Wash", ids.emailFor(defaultBusinessId), "+27821234567", null));
        defaultBranchId = ids.branch();
        marketplaceService.createBranch(TestAccess.platformAdministrator(), defaultBusinessId, new CreateBranchCommand(
                defaultBranchId, "Test Branch", "1 Test Street", null, "Cape Town", "Western Cape",
                "8001", "ZA", new BigDecimal("-33.9249"), new BigDecimal("18.4241"),
                "Africa/Johannesburg", true));
        replaceFullWeekOperatingHours(defaultBranchId, LocalTime.of(8, 0), LocalTime.of(17, 0));
        return defaultBranchId;
    }

    protected void replaceFullWeekOperatingHours(String branchId, LocalTime opensAt, LocalTime closesAt) {
        branchSchedulingService.replaceOperatingSchedule(
                TestAccess.platformAdministrator(), branchId, new ReplaceOperatingScheduleCommand(
                Arrays.stream(DayOfWeek.values())
                        .map(day -> new WeeklyOperatingIntervalCommand(day, opensAt, closesAt))
                        .toList()));
    }

    protected Booking createSavedBooking() {
        return createBooking(newBookingWithFixture(TestDates.future()));
    }

    protected Booking createConfirmedBooking() {
        Booking booking = createSavedBooking();
        return bookingService.confirmBooking(TestAccess.platformAdministrator(), booking.getBookingId());
    }

    protected Booking createConfirmedBooking(LocalDateTime scheduledDateTime) {
        Booking booking = createBooking(newBookingWithFixture(scheduledDateTime));
        return bookingService.confirmBooking(TestAccess.platformAdministrator(), booking.getBookingId());
    }

    protected Booking createSavedBooking(LocalDateTime scheduledDateTime, BookingStatus status) {
        Booking booking = createBooking(newBookingWithFixture(scheduledDateTime));
        booking.setStatus(status);
        return booking;
    }

    protected QueueEntry createSavedQueueEntry() {
        Booking booking = createConfirmedBooking();
        QueueEntry created = queueService.createQueueEntry(
                TestAccess.platformAdministrator(), ids.queueEntry(), booking.getBookingId(),
                booking.getService().getServiceId());
        return queueRepository.findById(created.getQueueEntryId()).orElseThrow();
    }

    protected QueueEntry createSavedQueueEntry(LocalDateTime scheduledDateTime, QueueStatus status) {
        Booking booking = createConfirmedBooking(scheduledDateTime);
        QueueEntry created = queueService.createQueueEntry(
                TestAccess.platformAdministrator(), ids.queueEntry(), booking.getBookingId(),
                booking.getService().getServiceId());
        QueueEntry queueEntry = queueRepository.findById(created.getQueueEntryId()).orElseThrow();
        queueEntry.setQueueStatus(status);
        if (!status.isActive()) queueEntry.updateQueueMetrics(queueEntry.getPosition(), 0);
        queueRepository.update(queueEntry);
        return queueEntry;
    }

    private Booking createBooking(Booking booking) {
        return bookingService.createBooking(
                TestAccess.platformAdministrator(), booking.getBookingId(), booking.getUser().getUserId(),
                booking.getVehicle().getVehicleId(), booking.getBranchId(), booking.getServiceOfferingId(),
                booking.getScheduledDateTime(), booking.getSpecialRequest());
    }
}
