package com.carwash.bootstrap;

import com.carwash.booking.domain.BookingRepository;
import com.carwash.booking.application.BookingPolicyProperties;
import com.carwash.queue.application.QueuePolicyProperties;
import com.carwash.notification.application.NotificationPolicyProperties;
import com.carwash.notification.domain.NotificationRepository;
import com.carwash.marketplace.domain.CarWashBranchRepository;
import com.carwash.marketplace.domain.CarWashBusinessRepository;
import com.carwash.marketplace.domain.BranchOperatingScheduleRepository;
import com.carwash.marketplace.domain.TemporaryBranchClosureRepository;
import com.carwash.queue.domain.QueueEntryRepository;
import com.carwash.catalog.domain.ServiceRepository;
import com.carwash.catalog.domain.ServiceOfferingRepository;
import com.carwash.identity.domain.UserRepository;
import com.carwash.vehicle.domain.VehicleRepository;
import com.carwash.booking.infrastructure.InMemoryBookingRepository;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import com.carwash.notification.infrastructure.InMemoryNotificationRepository;
import com.carwash.marketplace.infrastructure.InMemoryCarWashBranchRepository;
import com.carwash.marketplace.infrastructure.InMemoryCarWashBusinessRepository;
import com.carwash.marketplace.infrastructure.InMemoryBranchOperatingScheduleRepository;
import com.carwash.marketplace.infrastructure.InMemoryTemporaryBranchClosureRepository;
import com.carwash.queue.infrastructure.InMemoryQueueEntryRepository;
import com.carwash.catalog.infrastructure.InMemoryServiceRepository;
import com.carwash.catalog.infrastructure.InMemoryServiceOfferingRepository;
import com.carwash.identity.infrastructure.InMemoryUserRepository;
import com.carwash.vehicle.infrastructure.InMemoryVehicleRepository;
import com.carwash.access.application.UserCredentialService;
import com.carwash.notification.infrastructure.AtomicNotificationIdGenerator;
import com.carwash.booking.application.AvailabilityService;
import com.carwash.booking.application.BranchAvailabilityDecisionService;
import com.carwash.booking.application.BranchAvailabilityQuery;
import com.carwash.booking.application.BranchAvailabilitySearchService;
import com.carwash.booking.application.BookingManagementService;
import com.carwash.booking.application.BookingSlotPolicyService;
import com.carwash.reporting.application.DailySummaryReportService;
import com.carwash.notification.application.NotificationIdGenerator;
import com.carwash.notification.application.NotificationManagementService;
import com.carwash.marketplace.application.MarketplaceManagementService;
import com.carwash.marketplace.application.BranchSchedulingService;
import com.carwash.queue.application.QueueManagementService;
import com.carwash.queue.application.QueueQuery;
import com.carwash.queue.application.QueueOrderingService;
import com.carwash.catalog.application.ServiceCatalogService;
import com.carwash.catalog.application.ServiceDefinitionQuery;
import com.carwash.catalog.application.ServiceDefinitionUsageQuery;
import com.carwash.catalog.application.ServiceOfferingQuery;
import com.carwash.catalog.application.ServiceOfferingService;
import com.carwash.discovery.application.DistanceCalculator;
import com.carwash.discovery.application.NearbyBranchDiscoveryService;
import com.carwash.discovery.infrastructure.HaversineDistanceCalculator;
import com.carwash.marketplace.application.BranchScheduleQuery;
import com.carwash.marketplace.application.MarketplaceQuery;
import com.carwash.identity.application.UserManagementService;
import com.carwash.vehicle.application.VehicleManagementService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ApplicationCompositionConfig {

    @Bean
    public InMemoryDataCoordinator inMemoryDataCoordinator() {
        return new InMemoryDataCoordinator();
    }

    @Bean
    public UserRepository userRepository() {
        return new InMemoryUserRepository();
    }

    @Bean
    public VehicleRepository vehicleRepository() {
        return new InMemoryVehicleRepository();
    }

    @Bean
    public ServiceRepository serviceRepository() {
        return new InMemoryServiceRepository();
    }

    @Bean
    public ServiceOfferingRepository serviceOfferingRepository() {
        return new InMemoryServiceOfferingRepository();
    }

    @Bean
    public BookingRepository bookingRepository() {
        return new InMemoryBookingRepository();
    }

    @Bean
    public QueueEntryRepository queueEntryRepository() {
        return new InMemoryQueueEntryRepository();
    }

    @Bean
    public NotificationRepository notificationRepository() {
        return new InMemoryNotificationRepository();
    }

    @Bean
    public CarWashBusinessRepository carWashBusinessRepository() {
        return new InMemoryCarWashBusinessRepository();
    }

    @Bean
    public CarWashBranchRepository carWashBranchRepository() {
        return new InMemoryCarWashBranchRepository();
    }

    @Bean
    public BranchOperatingScheduleRepository branchOperatingScheduleRepository() {
        return new InMemoryBranchOperatingScheduleRepository();
    }

    @Bean
    public TemporaryBranchClosureRepository temporaryBranchClosureRepository() {
        return new InMemoryTemporaryBranchClosureRepository();
    }

    @Bean
    public NotificationIdGenerator notificationIdGenerator() {
        return new AtomicNotificationIdGenerator();
    }

    @Bean
    public MarketplaceManagementService marketplaceManagementService(
            CarWashBusinessRepository businessRepository,
            CarWashBranchRepository branchRepository,
            InMemoryDataCoordinator coordinator,
            Clock clock
    ) {
        return new MarketplaceManagementService(businessRepository, branchRepository, coordinator, clock);
    }

    @Bean
    public BranchSchedulingService branchSchedulingService(
            CarWashBusinessRepository businessRepository,
            CarWashBranchRepository branchRepository,
            BranchOperatingScheduleRepository scheduleRepository,
            TemporaryBranchClosureRepository closureRepository,
            InMemoryDataCoordinator coordinator,
            Clock clock
    ) {
        return new BranchSchedulingService(
                businessRepository,
                branchRepository,
                scheduleRepository,
                closureRepository,
                coordinator,
                clock
        );
    }

    @Bean
    public DistanceCalculator distanceCalculator() {
        return new HaversineDistanceCalculator();
    }

    @Bean
    public NearbyBranchDiscoveryService nearbyBranchDiscoveryService(
            MarketplaceQuery marketplaceQuery,
            BranchScheduleQuery branchScheduleQuery,
            ServiceOfferingQuery serviceOfferingQuery,
            ServiceDefinitionQuery serviceDefinitionQuery,
            DistanceCalculator distanceCalculator
    ) {
        return new NearbyBranchDiscoveryService(
                marketplaceQuery,
                branchScheduleQuery,
                serviceOfferingQuery,
                serviceDefinitionQuery,
                distanceCalculator
        );
    }

    @Bean
    public BookingSlotPolicyService bookingSlotPolicyService(
            BookingRepository bookingRepository,
            BookingPolicyProperties bookingPolicy,
            Clock clock
    ) {
        return new BookingSlotPolicyService(bookingRepository, bookingPolicy, clock);
    }

    @Bean
    public AvailabilityService availabilityService(
            ServiceRepository serviceRepository,
            BookingSlotPolicyService bookingSlotPolicyService,
            BookingPolicyProperties bookingPolicy,
            InMemoryDataCoordinator coordinator
    ) {
        return new AvailabilityService(
                serviceRepository,
                bookingSlotPolicyService,
                bookingPolicy,
                coordinator
        );
    }

    @Bean
    public BranchAvailabilityDecisionService branchAvailabilityDecisionService(
            BookingRepository bookingRepository,
            MarketplaceQuery marketplaceQuery,
            BranchScheduleQuery branchScheduleQuery,
            ServiceOfferingQuery serviceOfferingQuery,
            ServiceDefinitionQuery serviceDefinitionQuery,
            BookingPolicyProperties bookingPolicy,
            Clock clock
    ) {
        return new BranchAvailabilityDecisionService(
                bookingRepository,
                marketplaceQuery,
                branchScheduleQuery,
                serviceOfferingQuery,
                serviceDefinitionQuery,
                bookingPolicy,
                clock
        );
    }

    @Bean
    public BranchAvailabilitySearchService branchAvailabilitySearchService(
            MarketplaceQuery marketplaceQuery,
            ServiceOfferingQuery serviceOfferingQuery,
            ServiceDefinitionQuery serviceDefinitionQuery,
            BranchAvailabilityQuery branchAvailabilityQuery,
            QueueQuery queueQuery,
            DistanceCalculator distanceCalculator,
            InMemoryDataCoordinator coordinator,
            Clock clock
    ) {
        return new BranchAvailabilitySearchService(
                marketplaceQuery,
                serviceOfferingQuery,
                serviceDefinitionQuery,
                branchAvailabilityQuery,
                queueQuery,
                distanceCalculator,
                coordinator,
                clock
        );
    }

    @Bean
    public QueueOrderingService queueOrderingService(
            QueueEntryRepository queueEntryRepository,
            InMemoryDataCoordinator coordinator,
            QueuePolicyProperties queuePolicy,
            ServiceOfferingQuery serviceOfferingQuery
    ) {
        return new QueueOrderingService(queueEntryRepository, coordinator, queuePolicy, serviceOfferingQuery);
    }

    @Bean
    public ServiceDefinitionUsageQuery serviceDefinitionUsageQuery(
            BookingRepository bookingRepository,
            QueueEntryRepository queueEntryRepository
    ) {
        return new ServiceDefinitionUsageQuery() {
            @Override
            public boolean referencedByBooking(String serviceId) {
                return bookingRepository.existsByServiceId(serviceId);
            }

            @Override
            public boolean referencedByQueue(String serviceId) {
                return queueEntryRepository.existsByServiceId(serviceId);
            }
        };
    }

    @Bean
    public UserManagementService userManagementService(
            UserRepository userRepository,
            UserCredentialService credentialService,
            VehicleRepository vehicleRepository,
            BookingRepository bookingRepository,
            NotificationRepository notificationRepository,
            InMemoryDataCoordinator coordinator
    ) {
        return new UserManagementService(
                userRepository,
                credentialService,
                vehicleRepository,
                bookingRepository,
                notificationRepository,
                coordinator
        );
    }

    @Bean
    public VehicleManagementService vehicleManagementService(
            VehicleRepository vehicleRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository,
            InMemoryDataCoordinator coordinator
    ) {
        return new VehicleManagementService(
                vehicleRepository,
                userRepository,
                bookingRepository,
                coordinator
        );
    }

    @Bean
    public ServiceCatalogService serviceCatalogService(
            ServiceRepository serviceRepository,
            ServiceOfferingRepository serviceOfferingRepository,
            InMemoryDataCoordinator coordinator,
            ServiceDefinitionUsageQuery serviceDefinitionUsageQuery
    ) {
        return new ServiceCatalogService(
                serviceRepository,
                serviceOfferingRepository,
                serviceDefinitionUsageQuery,
                coordinator
        );
    }

    @Bean
    public ServiceOfferingService serviceOfferingService(
            ServiceOfferingRepository serviceOfferingRepository,
            ServiceRepository serviceRepository,
            MarketplaceQuery marketplaceQuery,
            InMemoryDataCoordinator coordinator,
            Clock clock
    ) {
        return new ServiceOfferingService(
                serviceOfferingRepository,
                serviceRepository,
                marketplaceQuery,
                coordinator,
                clock
        );
    }

    @Bean
    public NotificationManagementService notificationManagementService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository,
            InMemoryDataCoordinator coordinator,
            NotificationIdGenerator notificationIdGenerator,
            NotificationPolicyProperties notificationPolicy,
            Clock clock
    ) {
        return new NotificationManagementService(
                notificationRepository,
                userRepository,
                bookingRepository,
                coordinator,
                notificationIdGenerator,
                notificationPolicy,
                clock
        );
    }

    @Bean
    public BookingManagementService bookingManagementService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            VehicleRepository vehicleRepository,
            ServiceDefinitionQuery serviceDefinitionQuery,
            ServiceOfferingQuery serviceOfferingQuery,
            MarketplaceQuery marketplaceQuery,
            QueueEntryRepository queueEntryRepository,
            NotificationRepository notificationRepository,
            NotificationManagementService notificationManagementService,
            QueueOrderingService queueOrderingService,
            InMemoryDataCoordinator coordinator,
            BookingPolicyProperties bookingPolicy,
            BookingSlotPolicyService bookingSlotPolicyService,
            BranchAvailabilityDecisionService branchAvailabilityDecisionService,
            Clock clock
    ) {
        return new BookingManagementService(
                bookingRepository,
                userRepository,
                vehicleRepository,
                serviceDefinitionQuery,
                serviceOfferingQuery,
                marketplaceQuery,
                queueEntryRepository,
                notificationRepository,
                notificationManagementService,
                queueOrderingService,
                coordinator,
                bookingPolicy,
                bookingSlotPolicyService,
                branchAvailabilityDecisionService,
                clock
        );
    }

    @Bean
    public QueueManagementService queueManagementService(
            QueueEntryRepository queueEntryRepository,
            BookingRepository bookingRepository,
            ServiceOfferingQuery serviceOfferingQuery,
            MarketplaceQuery marketplaceQuery,
            NotificationManagementService notificationManagementService,
            InMemoryDataCoordinator coordinator,
            QueueOrderingService queueOrderingService,
            Clock clock
    ) {
        return new QueueManagementService(
                queueEntryRepository,
                bookingRepository,
                serviceOfferingQuery,
                marketplaceQuery,
                notificationManagementService,
                coordinator,
                queueOrderingService,
                clock
        );
    }

    @Bean
    public DailySummaryReportService dailySummaryReportService(
            BookingManagementService bookingManagementService,
            QueueManagementService queueManagementService,
            MarketplaceQuery marketplaceQuery,
            InMemoryDataCoordinator coordinator
    ) {
        return new DailySummaryReportService(
                bookingManagementService,
                queueManagementService,
                marketplaceQuery,
                coordinator
        );
    }
}
