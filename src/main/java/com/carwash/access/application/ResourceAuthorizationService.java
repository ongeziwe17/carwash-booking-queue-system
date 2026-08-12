package com.carwash.access.application;

import com.carwash.booking.application.BookingQuery;
import com.carwash.queue.application.QueueQuery;
import com.carwash.vehicle.application.VehicleQuery;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("resourceAuthorization")
public class ResourceAuthorizationService {

    private final VehicleQuery vehicles;
    private final BookingQuery bookings;
    private final QueueQuery queues;

    public ResourceAuthorizationService(VehicleQuery vehicles, BookingQuery bookings, QueueQuery queues) {
        this.vehicles = vehicles;
        this.bookings = bookings;
        this.queues = queues;
    }

    public boolean isSelf(Authentication authentication, String id) {
        return elevatedAdmin(authentication) || authentication != null && authentication.getName().equals(id);
    }

    public boolean canCreateFor(Authentication authentication, String id) {
        return operational(authentication) || authentication != null && authentication.getName().equals(id);
    }

    public boolean canAccessVehicle(Authentication authentication, String id) {
        return operational(authentication) || vehicles.findOwnerId(id)
                .map(ownerId -> authentication != null && authentication.getName().equals(ownerId))
                .orElse(true);
    }

    public boolean canAccessBooking(Authentication authentication, String id) {
        return operational(authentication) || bookings.findOwnerId(id)
                .map(ownerId -> authentication != null && authentication.getName().equals(ownerId))
                .orElse(true);
    }

    public boolean canAccessQueueEntry(Authentication authentication, String id) {
        return operational(authentication) || queues.findOwnerId(id)
                .map(ownerId -> authentication != null && authentication.getName().equals(ownerId))
                .orElse(true);
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
