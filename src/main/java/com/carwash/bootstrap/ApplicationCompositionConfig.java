package com.carwash.bootstrap;

import com.carwash.booking.domain.BookingRepository;
import com.carwash.booking.application.BookingPolicyProperties;
import com.carwash.queue.application.QueuePolicyProperties;
import com.carwash.notification.application.NotificationPolicyProperties;
import com.carwash.notification.domain.NotificationRepository;
import com.carwash.queue.domain.QueueEntryRepository;
import com.carwash.catalog.domain.ServiceRepository;
import com.carwash.identity.domain.UserRepository;
import com.carwash.vehicle.domain.VehicleRepository;
import com.carwash.booking.infrastructure.InMemoryBookingRepository;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import com.carwash.notification.infrastructure.InMemoryNotificationRepository;
import com.carwash.queue.infrastructure.InMemoryQueueEntryRepository;
import com.carwash.catalog.infrastructure.InMemoryServiceRepository;
import com.carwash.identity.infrastructure.InMemoryUserRepository;
import com.carwash.vehicle.infrastructure.InMemoryVehicleRepository;
import com.carwash.access.application.UserCredentialService;
import com.carwash.notification.infrastructure.AtomicNotificationIdGenerator;
import com.carwash.booking.application.AvailabilityService;
import com.carwash.booking.application.BookingManagementService;
import com.carwash.booking.application.BookingSlotPolicyService;
import com.carwash.reporting.application.DailySummaryReportService;
import com.carwash.notification.application.NotificationIdGenerator;
import com.carwash.notification.application.NotificationManagementService;
import com.carwash.queue.application.QueueManagementService;
import com.carwash.queue.application.QueueOrderingService;
import com.carwash.catalog.application.ServiceCatalogService;
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
    public NotificationIdGenerator notificationIdGenerator() {
        return new AtomicNotificationIdGenerator();
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
    public QueueOrderingService queueOrderingService(
            QueueEntryRepository queueEntryRepository,
            InMemoryDataCoordinator coordinator,
            QueuePolicyProperties queuePolicy
    ) {
        return new QueueOrderingService(queueEntryRepository, coordinator, queuePolicy);
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
            BookingRepository bookingRepository,
            QueueEntryRepository queueEntryRepository,
            InMemoryDataCoordinator coordinator,
            QueueOrderingService queueOrderingService
    ) {
        return new ServiceCatalogService(
                serviceRepository,
                bookingRepository,
                queueEntryRepository,
                coordinator,
                queueOrderingService
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
            ServiceRepository serviceRepository,
            QueueEntryRepository queueEntryRepository,
            NotificationRepository notificationRepository,
            NotificationManagementService notificationManagementService,
            QueueOrderingService queueOrderingService,
            InMemoryDataCoordinator coordinator,
            BookingPolicyProperties bookingPolicy,
            BookingSlotPolicyService bookingSlotPolicyService,
            Clock clock
    ) {
        return new BookingManagementService(
                bookingRepository,
                userRepository,
                vehicleRepository,
                serviceRepository,
                queueEntryRepository,
                notificationRepository,
                notificationManagementService,
                queueOrderingService,
                coordinator,
                bookingPolicy,
                bookingSlotPolicyService,
                clock
        );
    }

    @Bean
    public QueueManagementService queueManagementService(
            QueueEntryRepository queueEntryRepository,
            BookingRepository bookingRepository,
            ServiceRepository serviceRepository,
            NotificationManagementService notificationManagementService,
            InMemoryDataCoordinator coordinator,
            QueueOrderingService queueOrderingService,
            Clock clock
    ) {
        return new QueueManagementService(
                queueEntryRepository,
                bookingRepository,
                serviceRepository,
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
            InMemoryDataCoordinator coordinator
    ) {
        return new DailySummaryReportService(
                bookingManagementService,
                queueManagementService,
                coordinator
        );
    }
}
