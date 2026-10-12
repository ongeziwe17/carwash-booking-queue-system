package com.carwash.shared.application;

/** Mandatory scope is created by application authorization, never by a client filter. */
public record ListScope(String businessId, String branchId, String userId, boolean discoverable) {
    public static final ListScope GLOBAL = new ListScope(null, null, null, false);
}
