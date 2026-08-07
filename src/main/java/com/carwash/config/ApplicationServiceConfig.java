package com.carwash.config;

import com.carwash.repository.BookingRepository;
import com.carwash.repository.NotificationRepository;
import com.carwash.repository.QueueEntryRepository;
import com.carwash.repository.ServiceRepository;
import com.carwash.repository.UserRepository;
import com.carwash.repository.VehicleRepository;
import com.carwash.repository.inmemory.InMemoryBookingRepository;
import com.carwash.repository.inmemory.InMemoryDataCoordinator;
import com.carwash.repository.inmemory.InMemoryNotificationRepository;
import com.carwash.repository.inmemory.InMemoryQueueEntryRepository;
import com.carwash.repository.inmemory.InMemoryServiceRepository;
import com.carwash.repository.inmemory.InMemoryUserRepository;
import com.carwash.repository.inmemory.InMemoryVehicleRepository;
import com.carwash.security.UserCredentialService;
import com.carwash.service.AtomicNotificationIdGenerator;
import com.carwash.service.BookingManagementService;
import com.carwash.service.DailySummaryReportService;
import com.carwash.service.NotificationIdGenerator;
import com.carwash.service.NotificationManagementService;
import com.carwash.service.QueueManagementService;
import com.carwash.service.ServiceCatalogService;
import com.carwash.service.UserManagementService;
import com.carwash.service.VehicleManagementService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationServiceConfig {

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
            InMemoryDataCoordinator coordinator
    ) {
        return new ServiceCatalogService(
                serviceRepository,
                bookingRepository,
                queueEntryRepository,
                coordinator
        );
    }

    @Bean
    public NotificationManagementService notificationManagementService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository,
            InMemoryDataCoordinator coordinator,
            NotificationIdGenerator notificationIdGenerator
    ) {
        return new NotificationManagementService(
                notificationRepository,
                userRepository,
                bookingRepository,
                coordinator,
                notificationIdGenerator
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
            InMemoryDataCoordinator coordinator
    ) {
        return new BookingManagementService(
                bookingRepository,
                userRepository,
                vehicleRepository,
                serviceRepository,
                queueEntryRepository,
                notificationRepository,
                notificationManagementService,
                coordinator
        );
    }

    @Bean
    public QueueManagementService queueManagementService(
            QueueEntryRepository queueEntryRepository,
            BookingRepository bookingRepository,
            ServiceRepository serviceRepository,
            NotificationManagementService notificationManagementService,
            InMemoryDataCoordinator coordinator
    ) {
        return new QueueManagementService(
                queueEntryRepository,
                bookingRepository,
                serviceRepository,
                notificationManagementService,
                coordinator
        );
    }

    @Bean
    public DailySummaryReportService dailySummaryReportService(
            BookingRepository bookingRepository,
            QueueEntryRepository queueEntryRepository,
            InMemoryDataCoordinator coordinator
    ) {
        return new DailySummaryReportService(
                bookingRepository,
                queueEntryRepository,
                coordinator
        );
    }
}
