package com.carwash.recommendation.api.dto;

import com.carwash.recommendation.application.RecommendationPreference;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Ranked, customer-safe, point-in-time branch recommendation.")
public record BranchRecommendationResponse(
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
        @Schema(description = "Straight-line kilometres rounded to two decimals; raw distance drives ranking.")
        BigDecimal distanceKm,
        @Schema(description = "Branch queue estimate in minutes; null for a non-current branch-local date.")
        Integer queueWaitEstimateMin,
        int serviceDurationMin,
        @Schema(description = "Queue wait plus service duration; null when queue wait is unavailable.")
        Integer estimatedTotalTimeMin,
        BigDecimal price,
        int concurrentCapacity,
        int remainingCapacity,
        RecommendationPreference appliedPreference,
        @Schema(description = "Objective or weighted score from 0 to 1; the internal total is rounded once to six decimals.")
        BigDecimal recommendationScore,
        RecommendationScoreBreakdownResponse scoreBreakdown,
        String explanation
) {
}
