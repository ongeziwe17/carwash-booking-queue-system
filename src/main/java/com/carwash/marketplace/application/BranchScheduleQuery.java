package com.carwash.marketplace.application;

import java.time.Instant;

public interface BranchScheduleQuery {

    BranchOpenStatusSnapshot getOpenStatus(String branchId, Instant requestedAt);

    default BranchServiceWindowSnapshot getServiceWindowStatus(
            String branchId,
            Instant startsAt,
            Instant endsAt
    ) {
        BranchOpenStatusSnapshot start = getOpenStatus(branchId, startsAt);
        BranchOpenStatusSnapshot end = getOpenStatus(branchId, endsAt.minusNanos(1));
        boolean open = start.open() && end.open();
        return new BranchServiceWindowSnapshot(
                branchId, startsAt, endsAt, start.timezone(),
                start.branchLocalDateTime(), end.branchLocalDateTime(),
                start.effectiveActive() && end.effectiveActive(),
                start.withinWeeklyHours() && end.withinWeeklyHours(),
                start.temporarilyClosed() || end.temporarilyClosed(),
                open,
                start.applicableClosureId() != null
                        ? start.applicableClosureId() : end.applicableClosureId(),
                start.applicableClosureReason() != null
                        ? start.applicableClosureReason() : end.applicableClosureReason()
        );
    }
}
