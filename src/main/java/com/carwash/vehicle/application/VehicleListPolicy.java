package com.carwash.vehicle.application;

import com.carwash.shared.application.*;
import static com.carwash.shared.application.ListPolicy.*;

/** Public query names are intentionally independent of persistence properties. */
public final class VehicleListPolicy {
    private VehicleListPolicy() { }
    public static final ListPolicy POLICY = new ListPolicy(
            java.util.Set.of("id", "brand", "model"),
            java.util.List.of(SortOrder.asc("id")),
            java.util.Map.ofEntries(java.util.Map.entry("userId", text(64)), java.util.Map.entry("vehicleType", text(64))),
            java.util.Set.of("businessId"));
}
