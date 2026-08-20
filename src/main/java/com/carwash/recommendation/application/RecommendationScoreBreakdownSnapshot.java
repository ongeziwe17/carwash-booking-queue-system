package com.carwash.recommendation.application;

public record RecommendationScoreBreakdownSnapshot(
        RecommendationScoreComponentSnapshot distance,
        RecommendationScoreComponentSnapshot queueWait,
        RecommendationScoreComponentSnapshot totalTime,
        RecommendationScoreComponentSnapshot price
) {
}
