package com.carwash.recommendation.application;

import com.carwash.booking.application.BranchAvailabilityCandidateSnapshot;

import java.math.BigDecimal;
import java.util.Optional;

/** Replaceable provider for one lower-is-better recommendation metric. */
public interface RecommendationMetricProvider {

    RecommendationMetric metric();

    Optional<BigDecimal> value(BranchAvailabilityCandidateSnapshot candidate);
}
