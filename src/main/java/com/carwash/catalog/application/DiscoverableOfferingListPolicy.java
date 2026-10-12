package com.carwash.catalog.application;

import com.carwash.shared.application.*;
import static com.carwash.shared.application.ListPolicy.*;

/** Public query names are intentionally independent of persistence properties. */
public final class DiscoverableOfferingListPolicy {
    private DiscoverableOfferingListPolicy() { }
    public static final ListPolicy POLICY = new ListPolicy(
            java.util.Set.of("id", "price", "duration"),
            java.util.List.of(SortOrder.asc("id")),
            java.util.Map.ofEntries(java.util.Map.entry("serviceId", text(64))),
            java.util.Set.of());
}
