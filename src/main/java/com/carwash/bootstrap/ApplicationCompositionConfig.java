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
import com.carwash.identity.domain.RoleRepository;
import com.carwash.identity.domain.TenantMembershipRepository;
import com.carwash.vehicle.domain.VehicleRepository;
import com.carwash.booking.infrastructure.InMemoryBookingRepository;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import com.carwash.shared.infrastructure.InMemoryMutationLock;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.application.MutationLock;
import com.carwash.notification.infrastructure.InMemoryNotificationRepository;
import com.carwash.marketplace.infrastructure.InMemoryCarWashBranchRepository;
import com.carwash.marketplace.infrastructure.InMemoryCarWashBusinessRepository;
import com.carwash.marketplace.infrastructure.InMemoryBranchOperatingScheduleRepository;
import com.carwash.marketplace.infrastructure.InMemoryTemporaryBranchClosureRepository;
import com.carwash.queue.infrastructure.InMemoryQueueEntryRepository;
import com.carwash.catalog.infrastructure.InMemoryServiceRepository;
import com.carwash.catalog.infrastructure.InMemoryServiceOfferingRepository;
import com.carwash.identity.infrastructure.InMemoryUserRepository;
import com.carwash.identity.infrastructure.InMemoryRoleRepository;
import com.carwash.identity.infrastructure.InMemoryTenantMembershipRepository;
import com.carwash.vehicle.infrastructure.InMemoryVehicleRepository;
import com.carwash.access.application.UserCredentialService;
import com.carwash.notification.infrastructure.AtomicNotificationIdGenerator;
import com.carwash.booking.application.AvailabilityService;
import com.carwash.booking.application.BranchAvailabilityDecisionService;
import com.carwash.booking.application.BranchAvailabilityCandidateQuery;
import com.carwash.booking.application.BranchAvailabilityQuery;
import com.carwash.booking.application.BranchAvailabilitySearchService;
import com.carwash.booking.application.BookingManagementService;
import com.carwash.booking.application.BookingCapacityQuery;
import com.carwash.booking.application.BookingSlotPolicyService;
import com.carwash.reporting.application.DailySummaryReportService;
import com.carwash.notification.application.NotificationIdGenerator;
import com.carwash.notification.application.BookingNotificationPublisher;
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
import com.carwash.catalog.application.ServiceOfferingCapacityQuery;
import com.carwash.discovery.application.DistanceCalculator;
import com.carwash.discovery.application.NearbyBranchDiscoveryService;
import com.carwash.discovery.infrastructure.HaversineDistanceCalculator;
import com.carwash.marketplace.application.BranchScheduleQuery;
import com.carwash.marketplace.application.MarketplaceQuery;
import com.carwash.identity.application.UserManagementService;
import com.carwash.identity.application.TenantBusinessQuery;
import com.carwash.identity.application.TenantMembershipManagementService;
import com.carwash.vehicle.application.VehicleManagementService;
import com.carwash.recommendation.application.DistanceRecommendationMetricProvider;
import com.carwash.recommendation.application.PriceRecommendationMetricProvider;
import com.carwash.recommendation.application.QueueWaitRecommendationMetricProvider;
import com.carwash.recommendation.application.RecommendationProperties;
import com.carwash.recommendation.application.RecommendationService;
import com.carwash.recommendation.application.TotalTimeRecommendationMetricProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration
public class ApplicationCompositionConfig {

    @Bean
    @Profile("!postgres")
    public InMemoryDataCoordinator inMemoryDataCoordinator() {
        return new InMemoryDataCoordinator();
    }

    @Bean
    @Profile("!postgres")
    public MutationLock inMemoryMutationLock() {
        return new InMemoryMutationLock();
    }

    @Bean
    @Profile("!postgres")
    public UserRepository userRepository() {
        return new InMemoryUserRepository();
    }

    @Bean
    @Profile("!postgres")
    public RoleRepository roleRepository() {
        return new InMemoryRoleRepository();
    }

    @Bean
    @Profile("!postgres")
    public TenantMembershipRepository tenantMembershipRepository() {
        return new InMemoryTenantMembershipRepository();
    }

    @Bean
    @Profile("!postgres")
    public VehicleRepository vehicleRepository(BookingRepository bookingRepository) {
        return new InMemoryVehicleRepository(bookingRepository);
    }

    @Bean
    @Profile("!postgres")
    public ServiceRepository serviceRepository() {
        return new InMemoryServiceRepository();
    }

    @Bean
    @Profile("!postgres")
    public ServiceOfferingRepository serviceOfferingRepository(MarketplaceQuery marketplaceQuery) {
        return new InMemoryServiceOfferingRepository(marketplaceQuery);
    }

    @Bean
    @Profile("!postgres")
    public BookingRepository bookingRepository(MarketplaceQuery marketplaceQuery) {
        return new InMemoryBookingRepository(marketplaceQuery);
    }

    @Bean
    @Profile("!postgres")
    public QueueEntryRepository queueEntryRepository(MarketplaceQuery marketplaceQuery) {
        return new InMemoryQueueEntryRepository(marketplaceQuery);
    }

    @Bean
    @Profile("!postgres")
    public NotificationRepository notificationRepository(MarketplaceQuery marketplaceQuery) {
        return new InMemoryNotificationRepository(marketplaceQuery);
    }

    @Bean
    @Profile("!postgres")
    public CarWashBusinessRepository carWashBusinessRepository() {
        return new InMemoryCarWashBusinessRepository();
    }

    @Bean
    @Profile("!postgres")
    public CarWashBranchRepository carWashBranchRepository() {
        return new InMemoryCarWashBranchRepository();
    }

    @Bean
    @Profile("!postgres")
    public BranchOperatingScheduleRepository branchOperatingScheduleRepository(
            CarWashBranchRepository branchRepository
    ) {
        return new InMemoryBranchOperatingScheduleRepository(branchRepository);
    }

    @Bean
    @Profile("!postgres")
    public TemporaryBranchClosureRepository temporaryBranchClosureRepository(
            CarWashBranchRepository branchRepository
    ) {
        return new InMemoryTemporaryBranchClosureRepository(branchRepository);
    }

    @Bean
    @Profile("!postgres")
    public NotificationIdGenerator notificationIdGenerator() {
        return new AtomicNotificationIdGenerator();
    }

    @Bean
    public MarketplaceManagementService marketplaceManagementService(
            CarWashBusinessRepository businessRepository,
            CarWashBranchRepository branchRepository,
            DataTransactionOperations coordinator,
            MutationLock mutationLock,
            Clock clock
    ) {
        return new MarketplaceManagementService(
                businessRepository, branchRepository, coordinator, mutationLock, clock);
    }

    @Bean
    public BranchSchedulingService branchSchedulingService(
            CarWashBusinessRepository businessRepository,
            CarWashBranchRepository branchRepository,
            BranchOperatingScheduleRepository scheduleRepository,
            TemporaryBranchClosureRepository closureRepository,
            DataTransactionOperations coordinator,
            MutationLock mutationLock,
            Clock clock
    ) {
        return new BranchSchedulingService(
                businessRepository,
                branchRepository,
                scheduleRepository,
                closureRepository,
                coordinator,
                mutationLock,
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
            DataTransactionOperations coordinator
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
            DataTransactionOperations coordinator,
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
    public DistanceRecommendationMetricProvider distanceRecommendationMetricProvider() {
        return new DistanceRecommendationMetricProvider();
    }

    @Bean
    public QueueWaitRecommendationMetricProvider queueWaitRecommendationMetricProvider() {
        return new QueueWaitRecommendationMetricProvider();
    }

    @Bean
    public TotalTimeRecommendationMetricProvider totalTimeRecommendationMetricProvider() {
        return new TotalTimeRecommendationMetricProvider();
    }

    @Bean
    public PriceRecommendationMetricProvider priceRecommendationMetricProvider() {
        return new PriceRecommendationMetricProvider();
    }

    @Bean
    public RecommendationService recommendationService(
            BranchAvailabilityCandidateQuery candidateQuery,
            RecommendationProperties recommendationProperties,
            DistanceRecommendationMetricProvider distance,
            QueueWaitRecommendationMetricProvider queueWait,
            TotalTimeRecommendationMetricProvider totalTime,
            PriceRecommendationMetricProvider price
    ) {
        return new RecommendationService(
                candidateQuery,
                recommendationProperties,
                java.util.List.of(distance, queueWait, totalTime, price)
        );
    }

    @Bean
    public QueueOrderingService queueOrderingService(
            QueueEntryRepository queueEntryRepository,
            DataTransactionOperations coordinator,
            MutationLock mutationLock,
            QueuePolicyProperties queuePolicy,
            ServiceOfferingQuery serviceOfferingQuery
    ) {
        return new QueueOrderingService(
                queueEntryRepository, coordinator, mutationLock, queuePolicy, serviceOfferingQuery);
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
            TenantMembershipRepository tenantMembershipRepository,
            DataTransactionOperations coordinator,
            MutationLock mutationLock
    ) {
        return new UserManagementService(
                userRepository,
                credentialService,
                vehicleRepository,
                bookingRepository,
                notificationRepository,
                tenantMembershipRepository,
                coordinator,
                mutationLock
        );
    }

    @Bean
    public TenantBusinessQuery tenantBusinessQuery(MarketplaceQuery marketplaceQuery) {
        return businessId -> marketplaceQuery.findBusinessOptional(businessId).isPresent();
    }

    @Bean
    public TenantMembershipManagementService tenantMembershipManagementService(
            UserRepository userRepository,
            TenantMembershipRepository tenantMembershipRepository,
            TenantBusinessQuery tenantBusinessQuery,
            DataTransactionOperations coordinator,
            MutationLock mutationLock,
            Clock clock
    ) {
        return new TenantMembershipManagementService(
                userRepository,
                tenantMembershipRepository,
                tenantBusinessQuery,
                coordinator,
                mutationLock,
                clock
        );
    }

    @Bean
    public VehicleManagementService vehicleManagementService(
            VehicleRepository vehicleRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository,
            DataTransactionOperations coordinator,
            MutationLock mutationLock
    ) {
        return new VehicleManagementService(
                vehicleRepository,
                userRepository,
                bookingRepository,
                coordinator,
                mutationLock
        );
    }

    @Bean
    public ServiceCatalogService serviceCatalogService(
            ServiceRepository serviceRepository,
            ServiceOfferingRepository serviceOfferingRepository,
            DataTransactionOperations coordinator,
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
            DataTransactionOperations coordinator,
            MutationLock mutationLock,
            ServiceOfferingCapacityQuery capacityQuery,
            Clock clock
    ) {
        return new ServiceOfferingService(
                serviceOfferingRepository,
                serviceRepository,
                marketplaceQuery,
                coordinator,
                mutationLock,
                capacityQuery,
                clock
        );
    }

    @Bean
    public ServiceOfferingCapacityQuery serviceOfferingCapacityQuery(BookingRepository bookingRepository) {
        return new BookingCapacityQuery(bookingRepository);
    }

    @Bean
    public NotificationManagementService notificationManagementService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository,
            DataTransactionOperations coordinator,
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
            BookingNotificationPublisher notificationPublisher,
            QueueOrderingService queueOrderingService,
            DataTransactionOperations coordinator,
            MutationLock mutationLock,
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
                notificationPublisher,
                queueOrderingService,
                coordinator,
                mutationLock,
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
            BookingNotificationPublisher notificationPublisher,
            DataTransactionOperations coordinator,
            MutationLock mutationLock,
            QueueOrderingService queueOrderingService,
            Clock clock
    ) {
        return new QueueManagementService(
                queueEntryRepository,
                bookingRepository,
                serviceOfferingQuery,
                marketplaceQuery,
                notificationPublisher,
                coordinator,
                mutationLock,
                queueOrderingService,
                clock
        );
    }

    @Bean
    public DailySummaryReportService dailySummaryReportService(
            BookingManagementService bookingManagementService,
            QueueManagementService queueManagementService,
            MarketplaceQuery marketplaceQuery,
            DataTransactionOperations coordinator
    ) {
        return new DailySummaryReportService(
                bookingManagementService,
                queueManagementService,
                marketplaceQuery,
                coordinator
        );
    }
}
