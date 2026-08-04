package com.carwash.security;

import com.carwash.repository.BookingRepository;
import com.carwash.repository.QueueEntryRepository;
import com.carwash.repository.VehicleRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("resourceAuthorization")
public class ResourceAuthorizationService {
    private final VehicleRepository vehicles; private final BookingRepository bookings; private final QueueEntryRepository queues;
    public ResourceAuthorizationService(VehicleRepository vehicles, BookingRepository bookings, QueueEntryRepository queues) {
        this.vehicles=vehicles; this.bookings=bookings; this.queues=queues;
    }
    public boolean isSelf(Authentication a, String id) { return elevatedAdmin(a) || a != null && a.getName().equals(id); }
    public boolean canCreateFor(Authentication a, String id) { return operational(a) || a != null && a.getName().equals(id); }
    public boolean canAccessVehicle(Authentication a,String id) { return operational(a) || vehicles.findById(id).map(v->a.getName().equals(v.getUserId())).orElse(true); }
    public boolean canAccessBooking(Authentication a,String id) { return operational(a) || bookings.findById(id).map(b->b.getUser()!=null&&a.getName().equals(b.getUser().getUserId())).orElse(true); }
    public boolean canAccessQueueEntry(Authentication a,String id) { return operational(a) || queues.findById(id).map(q->q.getBooking()!=null&&q.getBooking().getUser()!=null&&a.getName().equals(q.getBooking().getUser().getUserId())).orElse(true); }
    public boolean canAccessNotifications(Authentication a,String id) { return elevatedAdmin(a) || a != null && a.getName().equals(id); }
    public boolean operational(Authentication a) { return has(a,"ROLE_STAFF")||has(a,"ROLE_BUSINESS_OWNER")||has(a,"ROLE_PLATFORM_ADMIN"); }
    private boolean elevatedAdmin(Authentication a){ return has(a,"ROLE_PLATFORM_ADMIN"); }
    private boolean has(Authentication a, String authority){ return a!=null&&a.getAuthorities().stream().anyMatch(x->authority.equals(x.getAuthority())); }
}
