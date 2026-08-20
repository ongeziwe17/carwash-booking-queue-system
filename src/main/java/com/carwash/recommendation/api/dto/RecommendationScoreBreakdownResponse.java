package com.carwash.recommendation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Deterministic score components used by the explainable recommendation engine.")
public record RecommendationScoreBreakdownResponse(
        RecommendationScoreComponentResponse distance,
        RecommendationScoreComponentResponse queueWait,
        RecommendationScoreComponentResponse totalTime,
        RecommendationScoreComponentResponse price
) {
}
