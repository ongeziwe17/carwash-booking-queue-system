package com.carwash.feedback.domain;

import java.time.Instant;

public record ReviewReport(String id, String reviewId, String businessId, String actorId, String reason,
        Instant createdAt, ReportState state, String resolutionReason, String resolvedBy, Instant resolvedAt) {
    public ReviewReport resolve(String reason, String actor, Instant at) {
        return new ReviewReport(id, reviewId, businessId, actorId, this.reason, createdAt,
                ReportState.RESOLVED, reason, actor, at);
    }
}
