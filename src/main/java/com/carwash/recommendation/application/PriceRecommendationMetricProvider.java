package com.carwash.recommendation.application;

import com.carwash.booking.application.BranchAvailabilityCandidateSnapshot;

import java.math.BigDecimal;
import java.util.Optional;

public final class PriceRecommendationMetricProvider implements RecommendationMetricProvider {

    @Override
    public RecommendationMetric metric() {
        return RecommendationMetric.PRICE;
    }

    @Override
    public Optional<BigDecimal> value(BranchAvailabilityCandidateSnapshot candidate) {
        return Optional.ofNullable(candidate.price());
    }
}
