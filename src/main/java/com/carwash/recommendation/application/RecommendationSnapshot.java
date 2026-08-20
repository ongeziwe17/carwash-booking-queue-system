package com.carwash.recommendation.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RecommendationSnapshot(
        int rank,
        String businessId,
        String businessName,
        String branchId,
        String branchName,
        String serviceOfferingId,
        String serviceId,
        String serviceName,
        String branchTimezone,
        OffsetDateTime availableStartAt,
        OffsetDateTime estimatedEndAt,
        BigDecimal distanceKm,
        Integer queueWaitEstimateMin,
        int serviceDurationMin,
        Integer estimatedTotalTimeMin,
        BigDecimal price,
        int concurrentCapacity,
        int remainingCapacity,
        RecommendationPreference appliedPreference,
        BigDecimal recommendationScore,
        RecommendationScoreBreakdownSnapshot scoreBreakdown,
        String explanation
) {
}
