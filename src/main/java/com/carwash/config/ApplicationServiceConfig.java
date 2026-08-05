package com.carwash.config;

import com.carwash.factory.RepositoryFactory;
import com.carwash.factory.StorageType;
import com.carwash.repository.*;
import com.carwash.service.*;
import com.carwash.security.UserCredentialService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationServiceConfig {

    @Bean
    public UserRepository userRepository() {
        return RepositoryFactory.getUserRepository(StorageType.MEMORY);
    }
    @Bean
    public VehicleRepository vehicleRepository() {
        return RepositoryFactory.getVehicleRepository(StorageType.MEMORY);
    }
    @Bean
    public ServiceRepository serviceRepository() {
        return RepositoryFactory.getServiceRepository(StorageType.MEMORY);
    }
    @Bean
    public BookingRepository bookingRepository() {
        return RepositoryFactory.getBookingRepository(StorageType.MEMORY);
    }
    @Bean
    public QueueEntryRepository queueEntryRepository() {
        return RepositoryFactory.getQueueEntryRepository(StorageType.MEMORY);
    }

    @Bean
    public NotificationRepository notificationRepository() {
        return RepositoryFactory.getNotificationRepository(StorageType.MEMORY);
    }

    @Bean
    public UserManagementService userManagementService(UserRepository userRepository, UserCredentialService credentialService) {
        return new UserManagementService(userRepository, credentialService);
    }

    @Bean
    public VehicleManagementService vehicleManagementService(VehicleRepository vehicleRepository, UserRepository userRepository) {
        return new VehicleManagementService(vehicleRepository, userRepository);
    }

    @Bean
    public ServiceCatalogService serviceCatalogService(ServiceRepository serviceRepository) {
        return new ServiceCatalogService(serviceRepository);
    }

    @Bean
    public NotificationManagementService notificationManagementService(NotificationRepository notificationRepository) {
        return new NotificationManagementService(notificationRepository);
    }

    @Bean
    public BookingManagementService bookingManagementService(BookingRepository bookingRepository, UserRepository userRepository, VehicleRepository vehicleRepository, ServiceRepository serviceRepository, NotificationManagementService notificationManagementService) {
        return new BookingManagementService(bookingRepository, userRepository, vehicleRepository, serviceRepository, notificationManagementService);
    }

    @Bean
    public QueueManagementService queueManagementService(QueueEntryRepository queueEntryRepository, BookingRepository bookingRepository, ServiceRepository serviceRepository, NotificationManagementService notificationManagementService) {
        return new QueueManagementService(queueEntryRepository, bookingRepository, serviceRepository, notificationManagementService);
    }

    @Bean
    public DailySummaryReportService dailySummaryReportService(BookingRepository bookingRepository, QueueEntryRepository queueEntryRepository) {
        return new DailySummaryReportService(bookingRepository, queueEntryRepository);
    }
}
