package com.carwash.recommendation.application;

import com.carwash.booking.application.BranchAvailabilityCandidateQuery;
import com.carwash.booking.application.BranchAvailabilityCandidateSnapshot;
import com.carwash.booking.application.BranchAvailabilitySearchCriteria;
import com.carwash.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationServiceTest {

    private static final Instant START = Instant.parse("2030-01-07T08:00:00Z");

    @Test
    void allPreferencesUseTheSameCandidatesAndObjectiveWinnersDiffer() {
        List<BranchAvailabilityCandidateSnapshot> candidates = List.of(
                candidate("branch-a", "offering-a", "1", 100, 100, "1000"),
                candidate("branch-b", "offering-b", "100", 1, 100, "1000"),
                candidate("branch-c", "offering-c", "100", 50, 1, "1000"),
                candidate("branch-d", "offering-d", "100", 100, 100, "1"),
                candidate("branch-e", "offering-e", "10", 10, 50, "10"));
        RecordingQuery query = new RecordingQuery(candidates);
        RecommendationService service = service(query);

        assertEquals("branch-a", service.recommend(criteria(RecommendationPreference.NEAREST)).getFirst().branchId());
        assertEquals("branch-b", service.recommend(criteria(RecommendationPreference.SHORTEST_QUEUE)).getFirst().branchId());
        assertEquals("branch-c", service.recommend(criteria(RecommendationPreference.FASTEST_TOTAL_TIME)).getFirst().branchId());
        assertEquals("branch-d", service.recommend(criteria(RecommendationPreference.LOWEST_PRICE)).getFirst().branchId());
        assertEquals("branch-e", service.recommend(criteria(RecommendationPreference.BEST_OVERALL)).getFirst().branchId());

        assertEquals(5, query.criteria.size());
        for (RecommendationPreference preference : RecommendationPreference.values()) {
            List<RecommendationSnapshot> result = service.recommend(criteria(preference));
            assertEquals(candidates.stream().map(BranchAvailabilityCandidateSnapshot::branchId).sorted().toList(),
                    result.stream().map(RecommendationSnapshot::branchId).sorted().toList());
        }
    }

    @Test
    void bestOverallContributionsReconcileAndUseExpectedNormalization() {
        RecommendationService service = service(new RecordingQuery(List.of(
                candidate("branch-a", "offering-a", "1", 0, 20, "100"),
                candidate("branch-b", "offering-b", "3", 10, 40, "200"))));

        RecommendationSnapshot best = service.recommend(criteria(RecommendationPreference.BEST_OVERALL)).getFirst();

        assertEquals("branch-a", best.branchId());
        assertEquals(new BigDecimal("1.000000"), best.scoreBreakdown().distance().normalizedScore());
        assertEquals(new BigDecimal("1.000000"), best.scoreBreakdown().queueWait().normalizedScore());
        assertEquals(new BigDecimal("1.000000"), best.scoreBreakdown().totalTime().normalizedScore());
        assertEquals(new BigDecimal("1.000000"), best.scoreBreakdown().price().normalizedScore());
        assertEquals(new BigDecimal("1.000000"), best.recommendationScore());
        assertEquals(best.recommendationScore(), contributionTotal(best));
    }

    @Test
    void highPrecisionWeightsRoundOverallOnceAndReconcileDisplayedContributions() {
        RecommendationProperties.Weights weights = new RecommendationProperties.Weights(
                new BigDecimal("0.1666666"),
                new BigDecimal("0.1666666"),
                new BigDecimal("0.1666666"),
                new BigDecimal("0.5000002"));
        RecommendationService service = service(new RecordingQuery(List.of(
                candidate("branch-a", "offering-a", "1", 10, 20, "100"))), weights);

        RecommendationSnapshot result = service.recommend(criteria(RecommendationPreference.BEST_OVERALL))
                .getFirst();

        assertEquals(new BigDecimal("1.000000"), result.recommendationScore());
        assertEquals(result.recommendationScore(), contributionTotal(result));
        assertEquals(new BigDecimal("0.166667"),
                result.scoreBreakdown().distance().weightedContribution());
        assertEquals(new BigDecimal("0.166667"),
                result.scoreBreakdown().queueWait().weightedContribution());
        assertEquals(new BigDecimal("0.166666"),
                result.scoreBreakdown().totalTime().weightedContribution());
        assertEquals(new BigDecimal("0.500000"),
                result.scoreBreakdown().price().weightedContribution());
    }

    @Test
    void zeroOneAndMixedOverallScoresRemainBoundedAndReconciled() {
        RecommendationProperties.Weights weights = new RecommendationProperties.Weights(
                new BigDecimal("0.1666666"),
                new BigDecimal("0.1666666"),
                new BigDecimal("0.1666666"),
                new BigDecimal("0.5000002"));
        RecommendationService service = service(new RecordingQuery(List.of(
                candidate("branch-best", "offering-best", "1", 10, 20, "100"),
                candidate("branch-mixed", "offering-mixed", "2", 20, 20, "200"),
                candidate("branch-worst", "offering-worst", "4", 40, 20, "400"))), weights);

        List<RecommendationSnapshot> results = service.recommend(criteria(RecommendationPreference.BEST_OVERALL));

        assertEquals(new BigDecimal("1.000000"), results.get(0).recommendationScore());
        assertEquals(new BigDecimal("0.666667"), results.get(1).recommendationScore());
        assertEquals(new BigDecimal("0.000000"), results.get(2).recommendationScore());
        for (RecommendationSnapshot result : results) {
            assertScoreBounded(result.recommendationScore());
            assertEquals(result.recommendationScore(), contributionTotal(result));
            assertScoreBounded(result.scoreBreakdown().distance().weightedContribution());
            assertScoreBounded(result.scoreBreakdown().queueWait().weightedContribution());
            assertScoreBounded(result.scoreBreakdown().totalTime().weightedContribution());
            assertScoreBounded(result.scoreBreakdown().price().weightedContribution());
        }
    }

    @Test
    void bestOverallNearTieRanksByUnroundedScoreAndRemainsDeterministic() {
        RecommendationProperties.Weights weights = new RecommendationProperties.Weights(
                new BigDecimal("0.5000001"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.4999999"));
        RecommendationService service = service(new RecordingQuery(List.of(
                candidate("branch-a", "offering-a", "2", 10, 20, "100"),
                candidate("branch-z", "offering-z", "1", 10, 20, "200"))), weights);
        RecommendationSearchCriteria criteria = criteria(RecommendationPreference.BEST_OVERALL);

        List<RecommendationSnapshot> first = service.recommend(criteria);
        List<RecommendationSnapshot> second = service.recommend(criteria);

        assertEquals(List.of("branch-z", "branch-a"),
                first.stream().map(RecommendationSnapshot::branchId).toList());
        assertEquals(new BigDecimal("0.500000"), first.get(0).recommendationScore());
        assertEquals(new BigDecimal("0.500000"), first.get(1).recommendationScore());
        assertEquals(first, second);
    }

    @Test
    void equalKnownValuesScoreOneAndTieBreakByBranchThenOffering() {
        RecommendationService service = service(new RecordingQuery(List.of(
                candidate("branch-b", "offering-z", "2", 5, 20, "100"),
                candidate("branch-a", "offering-z", "2", 5, 20, "100"),
                candidate("branch-a", "offering-a", "2", 5, 20, "100"))));

        for (RecommendationPreference preference : RecommendationPreference.values()) {
            List<RecommendationSnapshot> result = service.recommend(criteria(preference));
            assertEquals(List.of("offering-a", "offering-z", "offering-z"),
                    result.stream().map(RecommendationSnapshot::serviceOfferingId).toList());
            assertEquals(List.of("branch-a", "branch-a", "branch-b"),
                    result.stream().map(RecommendationSnapshot::branchId).toList());
            assertTrue(result.stream().allMatch(item -> item.recommendationScore().compareTo(BigDecimal.ONE) == 0));
        }
    }

    @Test
    void rawNearTieControlsOrderEvenWhenResponseDistanceRoundsEqual() {
        RecommendationService service = service(new RecordingQuery(List.of(
                candidate("branch-a", "offering-a", "1.0042", 0, 20, "100"),
                candidate("branch-z", "offering-z", "1.0041", 0, 20, "100"))));

        List<RecommendationSnapshot> result = service.recommend(criteria(RecommendationPreference.NEAREST));

        assertEquals(List.of("branch-z", "branch-a"),
                result.stream().map(RecommendationSnapshot::branchId).toList());
        assertEquals(new BigDecimal("1.00"), result.get(0).distanceKm());
        assertEquals(new BigDecimal("1.00"), result.get(1).distanceKm());
    }

    @Test
    void missingQueueAndTotalRemainNullScoreZeroAndRankAfterKnownValues() {
        RecommendationService service = service(new RecordingQuery(List.of(
                candidate("branch-future", "offering-future", "1", null, 15, "80"),
                candidate("branch-known", "offering-known", "2", 20, 30, "100"))));

        for (RecommendationPreference preference : List.of(
                RecommendationPreference.SHORTEST_QUEUE,
                RecommendationPreference.FASTEST_TOTAL_TIME)) {
            List<RecommendationSnapshot> result = service.recommend(criteria(preference));
            assertEquals(List.of("branch-known", "branch-future"),
                    result.stream().map(RecommendationSnapshot::branchId).toList());
            RecommendationSnapshot future = result.get(1);
            assertNull(future.queueWaitEstimateMin());
            assertNull(future.estimatedTotalTimeMin());
            assertFalse(future.scoreBreakdown().queueWait().available());
            assertFalse(future.scoreBreakdown().totalTime().available());
            assertEquals(new BigDecimal("0.000000"), future.scoreBreakdown().queueWait().normalizedScore());
            assertTrue(future.explanation().contains("unavailable"));
        }
    }

    @Test
    void repeatedRequestsProduceIdenticalOrderingScoresAndExplanations() {
        RecommendationService service = service(new RecordingQuery(List.of(
                candidate("branch-b", "offering-b", "2", 10, 20, "90"),
                candidate("branch-a", "offering-a", "1", 5, 30, "120"))));
        RecommendationSearchCriteria criteria = criteria(RecommendationPreference.BEST_OVERALL);

        List<RecommendationSnapshot> first = service.recommend(criteria);
        List<RecommendationSnapshot> second = service.recommend(criteria);

        assertEquals(first, second);
    }

    @Test
    void noCandidatesReturnEmptyAndConfiguredRadiusIsEnforced() {
        RecordingQuery empty = new RecordingQuery(List.of());
        RecommendationService service = service(empty);
        assertEquals(List.of(), service.recommend(criteria(RecommendationPreference.NEAREST)));
        assertEquals(new BigDecimal("50.00"), empty.criteria.getFirst().radiusKm());

        RecommendationSearchCriteria tooWide = new RecommendationSearchCriteria(
                BigDecimal.ZERO, BigDecimal.ZERO, "service-1", START,
                RecommendationPreference.NEAREST, new BigDecimal("50.01"));
        assertThrows(BusinessRuleViolationException.class, () -> service.recommend(tooWide));
    }

    @Test
    void everyMetricRequiresExactlyOneProvider() {
        RecordingQuery query = new RecordingQuery(List.of());
        RecommendationProperties properties = properties();
        assertThrows(IllegalArgumentException.class,
                () -> new RecommendationService(query, properties, List.of(new DistanceRecommendationMetricProvider())));
        assertThrows(IllegalArgumentException.class,
                () -> new RecommendationService(query, properties, List.of(
                        new DistanceRecommendationMetricProvider(),
                        new DistanceRecommendationMetricProvider(),
                        new QueueWaitRecommendationMetricProvider(),
                        new TotalTimeRecommendationMetricProvider(),
                        new PriceRecommendationMetricProvider())));
    }

    private RecommendationService service(BranchAvailabilityCandidateQuery query) {
        return service(query, properties().weights());
    }

    private RecommendationService service(
            BranchAvailabilityCandidateQuery query,
            RecommendationProperties.Weights weights
    ) {
        return new RecommendationService(query, new RecommendationProperties(weights, new BigDecimal("50.00")), List.of(
                new DistanceRecommendationMetricProvider(),
                new QueueWaitRecommendationMetricProvider(),
                new TotalTimeRecommendationMetricProvider(),
                new PriceRecommendationMetricProvider()));
    }

    private RecommendationProperties properties() {
        return new RecommendationProperties(new RecommendationProperties.Weights(
                new BigDecimal("0.25"), new BigDecimal("0.25"),
                new BigDecimal("0.25"), new BigDecimal("0.25")), new BigDecimal("50.00"));
    }

    private RecommendationSearchCriteria criteria(RecommendationPreference preference) {
        return new RecommendationSearchCriteria(
                BigDecimal.ZERO, BigDecimal.ZERO, "service-1", START, preference, null);
    }

    private BigDecimal contributionTotal(RecommendationSnapshot snapshot) {
        RecommendationScoreBreakdownSnapshot breakdown = snapshot.scoreBreakdown();
        return breakdown.distance().weightedContribution()
                .add(breakdown.queueWait().weightedContribution())
                .add(breakdown.totalTime().weightedContribution())
                .add(breakdown.price().weightedContribution());
    }

    private void assertScoreBounded(BigDecimal score) {
        assertTrue(score.compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(score.compareTo(BigDecimal.ONE) <= 0);
    }

    private BranchAvailabilityCandidateSnapshot candidate(
            String branchId,
            String offeringId,
            String distance,
            Integer queue,
            int duration,
            String price
    ) {
        OffsetDateTime startsAt = OffsetDateTime.ofInstant(START, ZoneOffset.UTC);
        return new BranchAvailabilityCandidateSnapshot(
                "business-" + branchId, "Business " + branchId, branchId, "Branch " + branchId,
                "UTC", offeringId, "service-1", "Wash", new BigDecimal(price), duration,
                startsAt, startsAt.plusMinutes(duration), 4, 3, queue, new BigDecimal(distance));
    }

    private static final class RecordingQuery implements BranchAvailabilityCandidateQuery {
        private final List<BranchAvailabilityCandidateSnapshot> result;
        private final List<BranchAvailabilitySearchCriteria> criteria = new ArrayList<>();

        private RecordingQuery(List<BranchAvailabilityCandidateSnapshot> result) {
            this.result = result;
        }

        @Override
        public List<BranchAvailabilityCandidateSnapshot> findEligibleCandidates(
                BranchAvailabilitySearchCriteria criteria
        ) {
            this.criteria.add(criteria);
            return result;
        }
    }
}
