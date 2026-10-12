package com.carwash.marketplace.application;

import com.carwash.shared.application.*;
import static com.carwash.shared.application.ListPolicy.*;

/** Public query names are intentionally independent of persistence properties. */
public final class BusinessListPolicy {
    private BusinessListPolicy() { }
    public static final ListPolicy POLICY = new ListPolicy(
            java.util.Set.of("id", "name", "registeredAt"),
            java.util.List.of(SortOrder.asc("id")),
            java.util.Map.ofEntries(java.util.Map.entry("status", choices("ACTIVE", "INACTIVE"))),
            java.util.Set.of());
}
