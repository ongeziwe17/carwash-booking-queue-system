package com.carwash.feedback.domain;

import java.time.Instant;

/** Original content and canonical associations are immutable; moderation creates a new snapshot. */
public record Review(String id, String bookingId, String customerId, String businessId, String branchId,
        String offeringId, String serviceId, int rating, String comment, Instant createdAt,
        PublicationState publication, String moderationReason, String moderatorId, Instant moderatedAt) {
    public Review moderate(PublicationState state, String reason, String actor, Instant at) {
        return new Review(id, bookingId, customerId, businessId, branchId, offeringId, serviceId,
                rating, comment, createdAt, state, reason, actor, at);
    }
}
