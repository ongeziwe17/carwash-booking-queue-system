package com.carwash.recommendation.application;

import java.math.BigDecimal;

public record RecommendationScoreComponentSnapshot(
        BigDecimal rawValue,
        boolean available,
        BigDecimal normalizedScore,
        BigDecimal configuredWeight,
        BigDecimal weightedContribution
) {
}
