package com.carwash.recommendation.application;

import com.carwash.booking.application.BranchAvailabilityCandidateQuery;
import com.carwash.booking.application.BranchAvailabilityCandidateSnapshot;
import com.carwash.booking.application.BranchAvailabilitySearchCriteria;
import com.carwash.shared.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RecommendationService {

    public static final int RESPONSE_SCORE_SCALE = 6;
    public static final int RESPONSE_DISTANCE_SCALE = 2;
    public static final int RESPONSE_PRICE_SCALE = 2;
    public static final RoundingMode RESPONSE_ROUNDING = RoundingMode.HALF_UP;
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private static final BigDecimal RESPONSE_SCORE_UNIT = BigDecimal.ONE.movePointLeft(RESPONSE_SCORE_SCALE);
    private static final List<RecommendationMetric> DISPLAY_RECONCILIATION_ORDER = List.of(
            RecommendationMetric.DISTANCE,
            RecommendationMetric.QUEUE_WAIT,
            RecommendationMetric.TOTAL_TIME,
            RecommendationMetric.PRICE);

    private final BranchAvailabilityCandidateQuery candidateQuery;
    private final RecommendationProperties properties;
    private final Map<RecommendationMetric, RecommendationMetricProvider> providers;

    public RecommendationService(
            BranchAvailabilityCandidateQuery candidateQuery,
            RecommendationProperties properties,
            List<RecommendationMetricProvider> metricProviders
    ) {
        this.candidateQuery = Objects.requireNonNull(candidateQuery, "Availability candidate query is required");
        this.properties = Objects.requireNonNull(properties, "Recommendation properties are required");
        Objects.requireNonNull(metricProviders, "Recommendation metric providers are required");
        EnumMap<RecommendationMetric, RecommendationMetricProvider> indexed =
                new EnumMap<>(RecommendationMetric.class);
        for (RecommendationMetricProvider provider : metricProviders) {
            Objects.requireNonNull(provider, "Recommendation metric provider is required");
            if (indexed.put(provider.metric(), provider) != null) {
                throw new IllegalArgumentException("Duplicate recommendation metric provider: " + provider.metric());
            }
        }
        if (indexed.size() != RecommendationMetric.values().length) {
            throw new IllegalArgumentException("Every recommendation metric must have exactly one provider");
        }
        this.providers = Map.copyOf(indexed);
    }

    public List<RecommendationSnapshot> recommend(RecommendationSearchCriteria criteria) {
        Objects.requireNonNull(criteria, "Recommendation search criteria are required");
        Objects.requireNonNull(criteria.preference(), "Recommendation preference is required");
        BigDecimal effectiveRadius = effectiveRadius(criteria.requestedMaxRadiusKm());
        List<BranchAvailabilityCandidateSnapshot> candidates = candidateQuery.findEligibleCandidates(
                new BranchAvailabilitySearchCriteria(
                        criteria.serviceId(), criteria.startsAt(), criteria.latitude(), criteria.longitude(),
                        effectiveRadius));
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<CandidateScore> scores = candidates.stream().map(this::rawScore).toList();
        normalize(scores);
        List<CandidateScore> ordered = scores.stream()
                .sorted(order(criteria.preference()))
                .toList();

        List<RecommendationSnapshot> result = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            result.add(snapshot(index + 1, criteria.preference(), ordered.get(index)));
        }
        return List.copyOf(result);
    }

    private BigDecimal effectiveRadius(BigDecimal requestedRadius) {
        if (requestedRadius == null) {
            return properties.maxRadiusKm();
        }
        if (requestedRadius.compareTo(properties.maxRadiusKm()) > 0) {
            throw new BusinessRuleViolationException(
                    "Recommendation radius must not exceed the configured maximum of "
                            + properties.maxRadiusKm().toPlainString() + " km");
        }
        return requestedRadius;
    }

    private CandidateScore rawScore(BranchAvailabilityCandidateSnapshot candidate) {
        EnumMap<RecommendationMetric, BigDecimal> raw = new EnumMap<>(RecommendationMetric.class);
        for (RecommendationMetric metric : RecommendationMetric.values()) {
            raw.put(metric, providers.get(metric).value(candidate).orElse(null));
        }
        return new CandidateScore(candidate, raw);
    }

    private void normalize(List<CandidateScore> scores) {
        for (RecommendationMetric metric : RecommendationMetric.values()) {
            List<BigDecimal> known = scores.stream()
                    .map(score -> score.raw(metric))
                    .filter(Objects::nonNull)
                    .toList();
            BigDecimal minimum = known.stream().min(BigDecimal::compareTo).orElse(null);
            BigDecimal maximum = known.stream().max(BigDecimal::compareTo).orElse(null);
            for (CandidateScore score : scores) {
                BigDecimal normalized = normalized(score.raw(metric), minimum, maximum);
                BigDecimal contribution = normalized.multiply(properties.weight(metric), CALCULATION_CONTEXT);
                score.put(metric, normalized, contribution);
            }
        }
    }

    private BigDecimal normalized(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        if (value == null || minimum == null || maximum == null) {
            return BigDecimal.ZERO;
        }
        if (maximum.compareTo(minimum) == 0) {
            return BigDecimal.ONE;
        }
        return maximum.subtract(value, CALCULATION_CONTEXT)
                .divide(maximum.subtract(minimum, CALCULATION_CONTEXT), CALCULATION_CONTEXT);
    }

    private Comparator<CandidateScore> order(RecommendationPreference preference) {
        Comparator<CandidateScore> objective = switch (preference) {
            case NEAREST -> metricOrder(RecommendationMetric.DISTANCE);
            case SHORTEST_QUEUE -> metricOrder(RecommendationMetric.QUEUE_WAIT);
            case FASTEST_TOTAL_TIME -> metricOrder(RecommendationMetric.TOTAL_TIME);
            case LOWEST_PRICE -> metricOrder(RecommendationMetric.PRICE);
            case BEST_OVERALL -> (left, right) -> right.overall().compareTo(left.overall());
        };
        return objective
                .thenComparing(score -> score.candidate().branchId())
                .thenComparing(score -> score.candidate().serviceOfferingId());
    }

    private Comparator<CandidateScore> metricOrder(RecommendationMetric metric) {
        return (left, right) -> {
            BigDecimal leftValue = left.raw(metric);
            BigDecimal rightValue = right.raw(metric);
            if (leftValue == null && rightValue == null) return 0;
            if (leftValue == null) return 1;
            if (rightValue == null) return -1;
            return leftValue.compareTo(rightValue);
        };
    }

    private RecommendationSnapshot snapshot(
            int rank,
            RecommendationPreference preference,
            CandidateScore score
    ) {
        BranchAvailabilityCandidateSnapshot candidate = score.candidate();
        BigDecimal recommendationScore = preference == RecommendationPreference.BEST_OVERALL
                ? responseOverall(score)
                : responseBoundedScore(score.normalized(metric(preference)));
        RecommendationScoreBreakdownSnapshot breakdown = breakdown(
                score,
                preference == RecommendationPreference.BEST_OVERALL ? recommendationScore : null);
        Integer totalTime = candidate.queueWaitEstimateMin() == null
                ? null
                : Math.addExact(candidate.queueWaitEstimateMin(), candidate.serviceDurationMin());
        return new RecommendationSnapshot(
                rank,
                candidate.businessId(),
                candidate.businessName(),
                candidate.branchId(),
                candidate.branchName(),
                candidate.serviceOfferingId(),
                candidate.serviceId(),
                candidate.serviceName(),
                candidate.timezone(),
                candidate.availableStartAt(),
                candidate.estimatedEndAt(),
                responseRaw(RecommendationMetric.DISTANCE, candidate.rawDistanceKm()),
                candidate.queueWaitEstimateMin(),
                candidate.serviceDurationMin(),
                totalTime,
                responseRaw(RecommendationMetric.PRICE, candidate.price()),
                candidate.concurrentCapacity(),
                candidate.capacityRemaining(),
                preference,
                recommendationScore,
                breakdown,
                explanation(preference, candidate, recommendationScore, totalTime));
    }

    private RecommendationScoreBreakdownSnapshot breakdown(
            CandidateScore score,
            BigDecimal displayedOverall
    ) {
        EnumMap<RecommendationMetric, RecommendationScoreComponentSnapshot> components =
                new EnumMap<>(RecommendationMetric.class);
        for (RecommendationMetric metric : DISPLAY_RECONCILIATION_ORDER) {
            components.put(metric, component(score, metric));
        }
        if (displayedOverall != null) {
            reconcileDisplayedContributions(score, components, displayedOverall);
        }
        return new RecommendationScoreBreakdownSnapshot(
                components.get(RecommendationMetric.DISTANCE),
                components.get(RecommendationMetric.QUEUE_WAIT),
                components.get(RecommendationMetric.TOTAL_TIME),
                components.get(RecommendationMetric.PRICE));
    }

    private RecommendationScoreComponentSnapshot component(
            CandidateScore score,
            RecommendationMetric metric
    ) {
        BigDecimal raw = score.raw(metric);
        return new RecommendationScoreComponentSnapshot(
                responseRaw(metric, raw),
                raw != null,
                responseBoundedScore(score.normalized(metric)),
                properties.weight(metric),
                responseBoundedScore(score.contribution(metric)));
    }

    private void reconcileDisplayedContributions(
            CandidateScore score,
            EnumMap<RecommendationMetric, RecommendationScoreComponentSnapshot> components,
            BigDecimal displayedOverall
    ) {
        EnumMap<RecommendationMetric, BigDecimal> displayed = new EnumMap<>(RecommendationMetric.class);
        EnumMap<RecommendationMetric, BigDecimal> remainders = new EnumMap<>(RecommendationMetric.class);
        BigDecimal displayedTotal = BigDecimal.ZERO;
        for (RecommendationMetric metric : DISPLAY_RECONCILIATION_ORDER) {
            BigDecimal internal = boundedScore(score.contribution(metric));
            BigDecimal floor = internal.setScale(RESPONSE_SCORE_SCALE, RoundingMode.DOWN);
            displayed.put(metric, floor);
            remainders.put(metric, internal.subtract(floor));
            displayedTotal = displayedTotal.add(floor);
        }

        int units = displayedOverall.subtract(displayedTotal)
                .movePointRight(RESPONSE_SCORE_SCALE)
                .intValueExact();
        List<RecommendationMetric> allocationOrder = new ArrayList<>(DISPLAY_RECONCILIATION_ORDER);
        allocationOrder.sort((left, right) -> {
            int remainderOrder = remainders.get(right).compareTo(remainders.get(left));
            if (remainderOrder != 0) {
                return remainderOrder;
            }
            return Integer.compare(
                    DISPLAY_RECONCILIATION_ORDER.indexOf(left),
                    DISPLAY_RECONCILIATION_ORDER.indexOf(right));
        });
        if (units < 0 || units > allocationOrder.size()) {
            throw new IllegalStateException("Invalid recommendation contribution display residual");
        }
        for (int index = 0; index < units; index++) {
            RecommendationMetric metric = allocationOrder.get(index);
            displayed.put(metric, displayed.get(metric).add(RESPONSE_SCORE_UNIT));
        }

        for (RecommendationMetric metric : DISPLAY_RECONCILIATION_ORDER) {
            RecommendationScoreComponentSnapshot component = components.get(metric);
            components.put(metric, new RecommendationScoreComponentSnapshot(
                    component.rawValue(),
                    component.available(),
                    component.normalizedScore(),
                    component.configuredWeight(),
                    responseBoundedScore(displayed.get(metric))));
        }
        BigDecimal reconciled = DISPLAY_RECONCILIATION_ORDER.stream()
                .map(metric -> components.get(metric).weightedContribution())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (reconciled.compareTo(displayedOverall) != 0) {
            throw new IllegalStateException("Unable to reconcile displayed recommendation contributions");
        }
    }

    private BigDecimal responseOverall(CandidateScore score) {
        return responseBoundedScore(score.overall());
    }

    private BigDecimal responseBoundedScore(BigDecimal value) {
        return boundedScore(value).setScale(RESPONSE_SCORE_SCALE, RESPONSE_ROUNDING);
    }

    private BigDecimal boundedScore(BigDecimal value) {
        return value.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    private BigDecimal responseRaw(RecommendationMetric metric, BigDecimal value) {
        if (value == null) return null;
        return switch (metric) {
            case DISTANCE -> value.setScale(RESPONSE_DISTANCE_SCALE, RESPONSE_ROUNDING);
            case PRICE -> value.setScale(RESPONSE_PRICE_SCALE, RESPONSE_ROUNDING);
            case QUEUE_WAIT, TOTAL_TIME -> value.setScale(0, RoundingMode.UNNECESSARY);
        };
    }

    private RecommendationMetric metric(RecommendationPreference preference) {
        return switch (preference) {
            case NEAREST -> RecommendationMetric.DISTANCE;
            case SHORTEST_QUEUE -> RecommendationMetric.QUEUE_WAIT;
            case FASTEST_TOTAL_TIME -> RecommendationMetric.TOTAL_TIME;
            case LOWEST_PRICE -> RecommendationMetric.PRICE;
            case BEST_OVERALL -> throw new IllegalArgumentException("BEST_OVERALL has no single objective metric");
        };
    }

    private String explanation(
            RecommendationPreference preference,
            BranchAvailabilityCandidateSnapshot candidate,
            BigDecimal recommendationScore,
            Integer totalTime
    ) {
        return switch (preference) {
            case NEAREST -> "Ranked by nearest eligible distance: "
                    + responseRaw(RecommendationMetric.DISTANCE, candidate.rawDistanceKm()).toPlainString() + " km.";
            case SHORTEST_QUEUE -> candidate.queueWaitEstimateMin() == null
                    ? "Queue wait is unavailable for this future branch-local date, so this candidate ranks after known queues."
                    : "Ranked by shortest known queue: " + candidate.queueWaitEstimateMin() + " minutes.";
            case FASTEST_TOTAL_TIME -> totalTime == null
                    ? "Queue wait and total time are unavailable for this future branch-local date, so this candidate ranks after known totals."
                    : "Ranked by fastest known completion: " + totalTime + " minutes ("
                    + candidate.queueWaitEstimateMin() + " queue + " + candidate.serviceDurationMin()
                    + " service).";
            case LOWEST_PRICE -> "Ranked by lowest eligible price: "
                    + responseRaw(RecommendationMetric.PRICE, candidate.price()).toPlainString() + ".";
            case BEST_OVERALL -> "Weighted balance score " + recommendationScore.toPlainString()
                    + " across distance, queue wait, total time, and price."
                    + (candidate.queueWaitEstimateMin() == null
                    ? " Queue wait and total time are unavailable and contribute zero."
                    : "");
        };
    }

    private static final class CandidateScore {
        private final BranchAvailabilityCandidateSnapshot candidate;
        private final EnumMap<RecommendationMetric, BigDecimal> raw;
        private final EnumMap<RecommendationMetric, BigDecimal> normalized =
                new EnumMap<>(RecommendationMetric.class);
        private final EnumMap<RecommendationMetric, BigDecimal> contributions =
                new EnumMap<>(RecommendationMetric.class);

        private CandidateScore(
                BranchAvailabilityCandidateSnapshot candidate,
                EnumMap<RecommendationMetric, BigDecimal> raw
        ) {
            this.candidate = candidate;
            this.raw = raw;
        }

        private BranchAvailabilityCandidateSnapshot candidate() {
            return candidate;
        }

        private BigDecimal raw(RecommendationMetric metric) {
            return raw.get(metric);
        }

        private BigDecimal normalized(RecommendationMetric metric) {
            return normalized.get(metric);
        }

        private BigDecimal contribution(RecommendationMetric metric) {
            return contributions.get(metric);
        }

        private void put(
                RecommendationMetric metric,
                BigDecimal normalizedScore,
                BigDecimal contribution
        ) {
            normalized.put(metric, normalizedScore);
            contributions.put(metric, contribution);
        }

        private BigDecimal overall() {
            return contributions.values().stream().reduce(BigDecimal.ZERO,
                    (left, right) -> left.add(right, CALCULATION_CONTEXT));
        }
    }
}
