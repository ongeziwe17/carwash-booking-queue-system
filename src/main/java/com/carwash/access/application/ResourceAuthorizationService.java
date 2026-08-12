package com.carwash.access.application;

import com.carwash.booking.domain.BookingRepository;
import com.carwash.queue.domain.QueueEntryRepository;
import com.carwash.vehicle.domain.VehicleRepository;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("resourceAuthorization")
public class ResourceAuthorizationService {

    private final VehicleRepository vehicles;
    private final BookingRepository bookings;
    private final QueueEntryRepository queues;
    private final InMemoryDataCoordinator coordinator;

    public ResourceAuthorizationService(VehicleRepository vehicles, BookingRepository bookings,
                                        QueueEntryRepository queues, InMemoryDataCoordinator coordinator) {
        this.vehicles = vehicles;
        this.bookings = bookings;
        this.queues = queues;
        this.coordinator = coordinator;
    }

    public boolean isSelf(Authentication authentication, String id) {
        return elevatedAdmin(authentication) || authentication != null && authentication.getName().equals(id);
    }

    public boolean canCreateFor(Authentication authentication, String id) {
        return operational(authentication) || authentication != null && authentication.getName().equals(id);
    }

    public boolean canAccessVehicle(Authentication authentication, String id) {
        return coordinator.read(() -> operational(authentication) || vehicles.findById(id)
                .map(vehicle -> authentication != null && authentication.getName().equals(vehicle.getUserId()))
                .orElse(true));
    }

    public boolean canAccessBooking(Authentication authentication, String id) {
        return coordinator.read(() -> operational(authentication) || bookings.findById(id)
                .map(booking -> authentication != null && booking.getUser() != null
                        && authentication.getName().equals(booking.getUser().getUserId()))
                .orElse(true));
    }

    public boolean canAccessQueueEntry(Authentication authentication, String id) {
        return coordinator.read(() -> operational(authentication) || queues.findById(id)
                .map(queue -> authentication != null && queue.getBooking() != null
                        && queue.getBooking().getUser() != null
                        && authentication.getName().equals(queue.getBooking().getUser().getUserId()))
                .orElse(true));
    }

    public boolean canAccessNotifications(Authentication authentication, String id) {
        return elevatedAdmin(authentication) || authentication != null && authentication.getName().equals(id);
    }

    public boolean operational(Authentication authentication) {
        return has(authentication, "ROLE_STAFF") || has(authentication, "ROLE_BUSINESS_OWNER")
                || has(authentication, "ROLE_PLATFORM_ADMIN");
    }

    private boolean elevatedAdmin(Authentication authentication) {
        return has(authentication, "ROLE_PLATFORM_ADMIN");
    }

    private boolean has(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(item -> authority.equals(item.getAuthority()));
    }
}
