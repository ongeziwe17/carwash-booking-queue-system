package com.carwash.testsupport;

import com.carwash.booking.domain.Booking;
import com.carwash.notification.domain.Notification;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.catalog.domain.Service;
import com.carwash.catalog.domain.ServiceOffering;
import com.carwash.identity.domain.User;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.marketplace.domain.CarWashBranch;
import com.carwash.marketplace.domain.CarWashBusiness;
import com.carwash.marketplace.domain.BranchOperatingSchedule;
import com.carwash.marketplace.domain.TemporaryBranchClosure;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.notification.domain.NotificationRepository;
import com.carwash.queue.domain.QueueEntryRepository;
import com.carwash.catalog.domain.ServiceRepository;
import com.carwash.catalog.domain.ServiceOfferingRepository;
import com.carwash.identity.domain.UserRepository;
import com.carwash.vehicle.domain.VehicleRepository;
import com.carwash.marketplace.domain.CarWashBranchRepository;
import com.carwash.marketplace.domain.CarWashBusinessRepository;
import com.carwash.marketplace.domain.BranchOperatingScheduleRepository;
import com.carwash.marketplace.domain.TemporaryBranchClosureRepository;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;

public final class InMemoryTestDataCleaner {

    private final InMemoryDataCoordinator coordinator;
    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final ServiceRepository services;
    private final ServiceOfferingRepository offerings;
    private final BookingRepository bookings;
    private final QueueEntryRepository queueEntries;
    private final NotificationRepository notifications;
    private final CarWashBusinessRepository businesses;
    private final CarWashBranchRepository branches;
    private final BranchOperatingScheduleRepository schedules;
    private final TemporaryBranchClosureRepository closures;
    private final DeterministicTestNotificationIdGenerator notificationIds;

    public InMemoryTestDataCleaner(
            InMemoryDataCoordinator coordinator,
            UserRepository users,
            VehicleRepository vehicles,
            ServiceRepository services,
            ServiceOfferingRepository offerings,
            BookingRepository bookings,
            QueueEntryRepository queueEntries,
            NotificationRepository notifications,
            CarWashBusinessRepository businesses,
            CarWashBranchRepository branches,
            BranchOperatingScheduleRepository schedules,
            TemporaryBranchClosureRepository closures,
            DeterministicTestNotificationIdGenerator notificationIds
    ) {
        this.coordinator = coordinator;
        this.users = users;
        this.vehicles = vehicles;
        this.services = services;
        this.offerings = offerings;
        this.bookings = bookings;
        this.queueEntries = queueEntries;
        this.notifications = notifications;
        this.businesses = businesses;
        this.branches = branches;
        this.schedules = schedules;
        this.closures = closures;
        this.notificationIds = notificationIds;
    }

    public void clean() {
        coordinator.write(() -> {
            offerings.findAll().stream().map(ServiceOffering::getOfferingId)
                    .forEach(offerings::deleteById);
            closures.findAll().stream().map(TemporaryBranchClosure::getClosureId)
                    .forEach(closures::deleteById);
            schedules.findAll().stream().map(BranchOperatingSchedule::getBranchId)
                    .forEach(schedules::deleteById);
            branches.findAll().stream().map(CarWashBranch::getBranchId)
                    .forEach(branches::deleteById);
            businesses.findAll().stream().map(CarWashBusiness::getBusinessId)
                    .forEach(businesses::deleteById);
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
