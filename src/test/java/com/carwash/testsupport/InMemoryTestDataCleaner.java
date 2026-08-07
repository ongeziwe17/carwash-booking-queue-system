package com.carwash.testsupport;

import com.carwash.domain.Booking;
import com.carwash.domain.Notification;
import com.carwash.domain.QueueEntry;
import com.carwash.domain.Service;
import com.carwash.domain.User;
import com.carwash.domain.Vehicle;
import com.carwash.repository.BookingRepository;
import com.carwash.repository.NotificationRepository;
import com.carwash.repository.QueueEntryRepository;
import com.carwash.repository.ServiceRepository;
import com.carwash.repository.UserRepository;
import com.carwash.repository.VehicleRepository;
import com.carwash.repository.inmemory.InMemoryDataCoordinator;

public final class InMemoryTestDataCleaner {

    private final InMemoryDataCoordinator coordinator;
    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final ServiceRepository services;
    private final BookingRepository bookings;
    private final QueueEntryRepository queueEntries;
    private final NotificationRepository notifications;
    private final DeterministicTestNotificationIdGenerator notificationIds;

    public InMemoryTestDataCleaner(
            InMemoryDataCoordinator coordinator,
            UserRepository users,
            VehicleRepository vehicles,
            ServiceRepository services,
            BookingRepository bookings,
            QueueEntryRepository queueEntries,
            NotificationRepository notifications,
            DeterministicTestNotificationIdGenerator notificationIds
    ) {
        this.coordinator = coordinator;
        this.users = users;
        this.vehicles = vehicles;
        this.services = services;
        this.bookings = bookings;
        this.queueEntries = queueEntries;
        this.notifications = notifications;
        this.notificationIds = notificationIds;
    }

    public void clean() {
        coordinator.write(() -> {
            notifications.findAll().stream().map(Notification::getNotificationId)
                    .forEach(notifications::deleteById);
            queueEntries.findAll().stream().map(QueueEntry::getQueueEntryId)
                    .forEach(queueEntries::deleteById);
            bookings.findAll().stream().map(Booking::getBookingId)
                    .forEach(bookings::deleteById);
            vehicles.findAll().stream().map(Vehicle::getVehicleId)
                    .forEach(vehicles::deleteById);
            services.findAll().stream().map(Service::getServiceId)
                    .forEach(services::deleteById);
            users.findAll().stream().map(User::getUserId)
                    .forEach(users::deleteById);
            notificationIds.reset();
        });
    }
}
