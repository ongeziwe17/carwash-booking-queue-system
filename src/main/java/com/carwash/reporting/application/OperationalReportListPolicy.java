package com.carwash.reporting.application;

import com.carwash.shared.application.ListPolicy;
import com.carwash.shared.application.SortOrder;
import java.util.*;

public final class OperationalReportListPolicy {
    private OperationalReportListPolicy() { }
    private static final Set<String> CONTEXT=Set.of("startDate","endDate","businessId","branchId");
    public static final ListPolicy DAILY = new ListPolicy(Set.of("id","date","bookings"),
            List.of(SortOrder.asc("date")),Map.of(),CONTEXT);
    public static final ListPolicy BRANCHES = new ListPolicy(Set.of("id","bookings"),
            List.of(new SortOrder("bookings",true)),Map.of(),CONTEXT);
    public static final ListPolicy OFFERINGS = new ListPolicy(Set.of("id","bookings","serviceId"),
            List.of(new SortOrder("bookings",true)),Map.of("serviceId",ListPolicy.text(64)),CONTEXT);
    public static final ListPolicy HOURS = new ListPolicy(Set.of("id","hour","bookings"),
            List.of(new SortOrder("bookings",true)),Map.of(),CONTEXT);
}
