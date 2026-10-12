package com.carwash.queue.application;

import com.carwash.shared.application.*;
import static com.carwash.shared.application.ListPolicy.*;

/** Public query names are intentionally independent of persistence properties. */
public final class QueueListPolicy {
    private QueueListPolicy() { }
    public static final ListPolicy POLICY = new ListPolicy(
            java.util.Set.of("id", "position", "joinedAt"),
            java.util.List.of(SortOrder.asc("branchId"), SortOrder.asc("activeRank"), SortOrder.asc("position"), SortOrder.asc("joinedAt"), SortOrder.asc("id")),
            java.util.Map.ofEntries(java.util.Map.entry("status", choices("WAITING", "CALLED", "IN_PROGRESS", "COMPLETED", "EXITED")), java.util.Map.entry("serviceId", text(64)), java.util.Map.entry("offeringId", text(64))),
            java.util.Set.of("branchId", "businessId"));
}
