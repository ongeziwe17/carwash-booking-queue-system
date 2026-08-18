package com.carwash.booking.application;

/** Bounded internal reason for a branch availability decision. */
public enum BranchAvailabilityReason {
    AVAILABLE,
    NOT_IN_FUTURE,
    INVALID_LOCAL_TIME,
    MISALIGNED_SLOT,
    INACTIVE_BRANCH,
    INACTIVE_OFFERING,
    INACTIVE_SERVICE,
    OUTSIDE_OPERATING_HOURS,
    TEMPORARILY_CLOSED,
    CAPACITY_FULL
}
