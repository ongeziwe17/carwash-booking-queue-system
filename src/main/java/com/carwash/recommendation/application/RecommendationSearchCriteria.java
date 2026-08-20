package com.carwash.recommendation.application;

import java.math.BigDecimal;
import java.time.Instant;

public record RecommendationSearchCriteria(
        BigDecimal latitude,
        BigDecimal longitude,
        String serviceId,
        Instant startsAt,
        RecommendationPreference preference,
        BigDecimal requestedMaxRadiusKm
) {
}
