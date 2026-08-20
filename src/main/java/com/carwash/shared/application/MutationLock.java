package com.carwash.shared.application;

import java.util.Collection;

/** Cross-instance serialization port for invariants evaluated before mutation. */
public interface MutationLock {

    void acquire(Collection<String> keys);

    static MutationLock noOp() {
        return keys -> { };
    }

    default void acquire(String key) {
        acquire(java.util.List.of(key));
    }

    static String offering(String offeringId) {
        return "02:offering:" + offeringId;
    }

    static String booking(String bookingId) {
        return "01:booking:" + bookingId;
    }

    static String customer(String userId) {
        return "03:customer:" + userId;
    }

    static String vehicle(String vehicleId) {
        return "04:vehicle:" + vehicleId;
    }

    static String queueBranch(String branchId) {
        return "05:queue-branch:" + branchId;
    }

    static String scheduleBranch(String branchId) {
        return "06:schedule-branch:" + branchId;
    }

    static String platformAdministrators() {
        return "07:platform-administrators";
    }
}
