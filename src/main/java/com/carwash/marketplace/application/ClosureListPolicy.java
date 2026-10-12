package com.carwash.marketplace.application;

import com.carwash.shared.application.*;
import static com.carwash.shared.application.ListPolicy.*;

/** Public query names are intentionally independent of persistence properties. */
public final class ClosureListPolicy {
    private ClosureListPolicy() { }
    public static final ListPolicy POLICY = new ListPolicy(
            java.util.Set.of("id", "startsAt", "endsAt"),
            java.util.List.of(SortOrder.asc("startsAt"), SortOrder.asc("id")),
            java.util.Map.ofEntries(java.util.Map.entry("status", choices("ACTIVE", "CANCELLED")), java.util.Map.entry("from", INSTANT), java.util.Map.entry("to", INSTANT)),
            java.util.Set.of());
}
