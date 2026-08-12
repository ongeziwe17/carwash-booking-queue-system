package com.carwash.testsupport;

import com.carwash.booking.domain.Booking;
import com.carwash.notification.domain.Notification;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.catalog.domain.Service;
import com.carwash.identity.domain.User;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.notification.domain.NotificationRepository;
import com.carwash.queue.domain.QueueEntryRepository;
import com.carwash.catalog.domain.ServiceRepository;
import com.carwash.identity.domain.UserRepository;
import com.carwash.vehicle.domain.VehicleRepository;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;

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
