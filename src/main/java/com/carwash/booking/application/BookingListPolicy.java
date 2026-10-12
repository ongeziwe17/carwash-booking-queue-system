package com.carwash.booking.application;

import com.carwash.shared.application.*;
import static com.carwash.shared.application.ListPolicy.*;

/** Public query names are intentionally independent of persistence properties. */
public final class BookingListPolicy {
    private BookingListPolicy() { }
    public static final ListPolicy POLICY = new ListPolicy(
            java.util.Set.of("id", "scheduledAt", "createdAt"),
            java.util.List.of(SortOrder.asc("id")),
            java.util.Map.ofEntries(java.util.Map.entry("status", choices("CREATED", "CONFIRMED", "CANCELLED", "IN_SERVICE", "COMPLETED")), java.util.Map.entry("userId", text(64)), java.util.Map.entry("serviceId", text(64)), java.util.Map.entry("offeringId", text(64)), java.util.Map.entry("from", LOCAL_TIME), java.util.Map.entry("to", LOCAL_TIME)),
            java.util.Set.of("branchId", "businessId"));
}
