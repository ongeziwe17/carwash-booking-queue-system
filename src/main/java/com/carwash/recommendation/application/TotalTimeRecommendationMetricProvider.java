package com.carwash.recommendation.application;

import com.carwash.booking.application.BranchAvailabilityCandidateSnapshot;

import java.math.BigDecimal;
import java.util.Optional;

public final class TotalTimeRecommendationMetricProvider implements RecommendationMetricProvider {

    @Override
    public RecommendationMetric metric() {
        return RecommendationMetric.TOTAL_TIME;
    }

    @Override
    public Optional<BigDecimal> value(BranchAvailabilityCandidateSnapshot candidate) {
        if (candidate.queueWaitEstimateMin() == null) {
            return Optional.empty();
        }
        return Optional.of(BigDecimal.valueOf(
                (long) candidate.queueWaitEstimateMin() + candidate.serviceDurationMin()));
    }
}
