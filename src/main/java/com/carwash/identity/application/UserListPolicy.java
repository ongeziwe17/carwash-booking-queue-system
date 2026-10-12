package com.carwash.identity.application;

import com.carwash.shared.application.*;
import static com.carwash.shared.application.ListPolicy.*;

/** Public query names are intentionally independent of persistence properties. */
public final class UserListPolicy {
    private UserListPolicy() { }
    public static final ListPolicy POLICY = new ListPolicy(
            java.util.Set.of("id", "name", "createdAt"),
            java.util.List.of(SortOrder.asc("id")),
            java.util.Map.ofEntries(java.util.Map.entry("accountStatus", choices("PENDING", "ACTIVE", "SUSPENDED", "DEACTIVATED"))),
            java.util.Set.of());
}
