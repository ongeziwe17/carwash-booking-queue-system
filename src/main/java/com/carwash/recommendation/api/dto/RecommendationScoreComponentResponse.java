package com.carwash.recommendation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "One lower-is-better metric's raw value and explainable normalized contribution.")
public record RecommendationScoreComponentResponse(
        @Schema(description = "Metric value at its documented response scale; null when unavailable.")
        BigDecimal rawValue,
        boolean available,
        @Schema(description = "Normalized component score from 0 to 1, rounded to six decimals.")
        BigDecimal normalizedScore,
        @Schema(description = "Configured decimal-safe BEST_OVERALL weight.")
        BigDecimal configuredWeight,
        @Schema(description = "Internal normalized score multiplied by weight, rounded to six decimals.")
        BigDecimal weightedContribution
) {
}
