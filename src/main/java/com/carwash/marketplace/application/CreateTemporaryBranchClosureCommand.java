package com.carwash.marketplace.application;

import java.time.Instant;

public record CreateTemporaryBranchClosureCommand(
        String closureId,
        Instant startAt,
        Instant endAt,
        String reason
) {
    public CreateTemporaryBranchClosureCommand {
        closureId = trim(closureId);
        reason = trim(reason);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
