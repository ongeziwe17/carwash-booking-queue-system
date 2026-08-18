package com.carwash.marketplace.application;

import java.time.Instant;

public interface BranchScheduleQuery {

    BranchOpenStatusSnapshot getOpenStatus(String branchId, Instant requestedAt);

    BranchServiceWindowSnapshot getServiceWindowStatus(
            String branchId,
            Instant startsAt,
            Instant endsAt
    );
}
