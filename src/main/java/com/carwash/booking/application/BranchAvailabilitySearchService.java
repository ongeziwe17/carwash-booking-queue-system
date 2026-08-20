package com.carwash.booking.application;

import com.carwash.catalog.application.ServiceDefinitionQuery;
import com.carwash.catalog.application.ServiceDefinitionSnapshot;
import com.carwash.catalog.application.ServiceOfferingQuery;
import com.carwash.catalog.application.ServiceOfferingSnapshot;
import com.carwash.discovery.application.DistanceCalculator;
import com.carwash.discovery.domain.GeoCoordinate;
import com.carwash.marketplace.application.BranchSnapshot;
import com.carwash.marketplace.application.BusinessSnapshot;
import com.carwash.marketplace.application.MarketplaceQuery;
import com.carwash.queue.application.QueueQuery;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class BranchAvailabilitySearchService implements BranchAvailabilityCandidateQuery {

    public static final int RESPONSE_DISTANCE_SCALE = 2;
    public static final RoundingMode RESPONSE_DISTANCE_ROUNDING = RoundingMode.HALF_UP;

    private final MarketplaceQuery marketplaceQuery;
    private final ServiceOfferingQuery serviceOfferingQuery;
    private final ServiceDefinitionQuery serviceDefinitionQuery;
    private final BranchAvailabilityQuery branchAvailabilityQuery;
    private final QueueQuery queueQuery;
    private final DistanceCalculator distanceCalculator;
    private final InMemoryDataCoordinator coordinator;
    private final Clock clock;

    public BranchAvailabilitySearchService(
            MarketplaceQuery marketplaceQuery,
            ServiceOfferingQuery serviceOfferingQuery,
            ServiceDefinitionQuery serviceDefinitionQuery,
            BranchAvailabilityQuery branchAvailabilityQuery,
            QueueQuery queueQuery,
            DistanceCalculator distanceCalculator,
            InMemoryDataCoordinator coordinator,
            Clock clock
    ) {
        this.marketplaceQuery = Objects.requireNonNull(marketplaceQuery, "Marketplace query is required");
        this.serviceOfferingQuery = Objects.requireNonNull(serviceOfferingQuery, "Service offering query is required");
        this.serviceDefinitionQuery = Objects.requireNonNull(serviceDefinitionQuery,
                "Service definition query is required");
        this.branchAvailabilityQuery = Objects.requireNonNull(branchAvailabilityQuery,
                "Branch availability query is required");
        this.queueQuery = Objects.requireNonNull(queueQuery, "Queue query is required");
        this.distanceCalculator = Objects.requireNonNull(distanceCalculator, "Distance calculator is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.clock = Objects.requireNonNull(clock, "Application clock is required");
    }

    public List<BranchAvailabilitySnapshot> findAvailableBranches(BranchAvailabilitySearchCriteria criteria) {
        validateSearch(criteria);
        return coordinator.read(() -> orderedCandidates(criteria).stream()
                .map(this::publicSnapshot)
                .toList());
    }

    @Override
    public List<BranchAvailabilityCandidateSnapshot> findEligibleCandidates(
            BranchAvailabilitySearchCriteria criteria
    ) {
        validateSearch(criteria);
        if (!criteria.hasOrigin()) {
            throw new BusinessRuleViolationException(
                    "Recommendation candidates require latitude and longitude");
        }
        return coordinator.read(() -> orderedCandidates(criteria).stream()
                .map(this::candidateSnapshot)
                .toList());
    }

    private void validateSearch(BranchAvailabilitySearchCriteria criteria) {
        Objects.requireNonNull(criteria, "Branch availability search criteria are required");
        if (!criteria.startsAt().isAfter(clock.instant())) {
            throw new BusinessRuleViolationException("Availability start instant must be in the future");
        }
    }

    private List<Candidate> orderedCandidates(BranchAvailabilitySearchCriteria criteria) {
        ServiceDefinitionSnapshot service = serviceDefinitionQuery
                .findServiceDefinitionOptional(criteria.serviceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + criteria.serviceId()));
        if (!service.active()) {
            return List.of();
        }

        GeoCoordinate origin = criteria.hasOrigin() ? criteria.origin() : null;
        List<Candidate> candidates = new ArrayList<>();
        for (BranchSnapshot branch : marketplaceQuery.findDiscoverableBranches()) {
            if (!branch.discoverable() || !branch.effectiveActive() || !branch.publicDiscoveryEnabled()) {
                continue;
            }
            ServiceOfferingSnapshot offering = serviceOfferingQuery
                    .findOfferingByBranchAndService(branch.branchId(), service.serviceId())
                    .filter(candidate -> canonicalDiscoverableOffering(candidate, branch, service.serviceId()))
                    .orElse(null);
            if (offering == null) {
                continue;
            }

            double rawDistance = 0.0d;
            if (origin != null) {
                GeoCoordinate destination = validStoredCoordinate(branch);
                if (destination == null) {
                    continue;
                }
                rawDistance = distanceCalculator.distanceKm(origin, destination);
                if (!Double.isFinite(rawDistance) || rawDistance < 0.0d) {
                    continue;
                }
                if (criteria.radiusKm() != null
                        && Double.compare(rawDistance, criteria.radiusKm().doubleValue()) > 0) {
                    continue;
                }
            }

            BranchAvailabilityDecisionSnapshot decision = branchAvailabilityQuery.evaluate(
                    branch.branchId(), offering.offeringId(), criteria.startsAt(), null);
            if (!decision.available()) {
                continue;
            }
            Integer queueWait = queueRelevant(criteria, branch)
                    ? queueQuery.estimateWaitMinutesForNewWork(branch.branchId())
                    : null;
            candidates.add(new Candidate(rawDistance, origin != null, branch, decision, queueWait));
        }

        Comparator<Candidate> order = origin == null
                ? Comparator.comparing(candidate -> candidate.branch().branchId())
                : Comparator.comparingDouble(Candidate::rawDistanceKm)
                        .thenComparing(candidate -> candidate.branch().branchId());
        return candidates.stream().sorted(order).toList();
    }

    private boolean canonicalDiscoverableOffering(
            ServiceOfferingSnapshot offering,
            BranchSnapshot branch,
            String serviceId
    ) {
        return offering.branchId().equals(branch.branchId())
                && offering.serviceId().equals(serviceId)
                && offering.effectiveActive()
                && offering.discoverable();
    }

    private boolean queueRelevant(BranchAvailabilitySearchCriteria criteria, BranchSnapshot branch) {
        ZoneId zone = ZoneId.of(branch.timezone());
        LocalDate requestedDate = criteria.startsAt().atZone(zone).toLocalDate();
        return requestedDate.equals(LocalDate.now(clock.withZone(zone)));
    }

    private GeoCoordinate validStoredCoordinate(BranchSnapshot branch) {
        try {
            if (branch.latitude() == null || branch.longitude() == null) {
                return null;
            }
            return new GeoCoordinate(branch.latitude().doubleValue(), branch.longitude().doubleValue());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private BranchAvailabilitySnapshot publicSnapshot(Candidate candidate) {
        BranchSnapshot branch = candidate.branch();
        BranchAvailabilityDecisionSnapshot decision = candidate.decision();
        BigDecimal distance = !candidate.hasOrigin() ? null : rawDistance(candidate)
                .setScale(RESPONSE_DISTANCE_SCALE, RESPONSE_DISTANCE_ROUNDING);
        return new BranchAvailabilitySnapshot(
                branch.branchId(), branch.businessId(), branch.branchName(), branch.addressLine1(),
                branch.addressLine2(), branch.city(), branch.province(), branch.postalCode(), branch.countryCode(),
                branch.latitude(), branch.longitude(), branch.timezone(), decision.serviceOfferingId(),
                decision.serviceId(), decision.serviceName(), decision.price(), decision.estimatedDurationMin(),
                decision.branchLocalStartsAt(), decision.branchLocalEndsAt(), decision.concurrentCapacity(),
                decision.capacityRemaining(), candidate.queueWaitEstimateMin(), distance);
    }

    private BranchAvailabilityCandidateSnapshot candidateSnapshot(Candidate candidate) {
        BranchSnapshot branch = candidate.branch();
        BranchAvailabilityDecisionSnapshot decision = candidate.decision();
        BusinessSnapshot business = marketplaceQuery.findBusinessOptional(branch.businessId())
                .orElseThrow(() -> new IllegalStateException(
                        "Discoverable branch has no owning business projection: " + branch.branchId()));
        return new BranchAvailabilityCandidateSnapshot(
                business.businessId(), business.businessName(), branch.branchId(), branch.branchName(),
                branch.timezone(), decision.serviceOfferingId(), decision.serviceId(), decision.serviceName(),
                decision.price(), decision.estimatedDurationMin(), decision.branchLocalStartsAt(),
                decision.branchLocalEndsAt(), decision.concurrentCapacity(), decision.capacityRemaining(),
                candidate.queueWaitEstimateMin(), rawDistance(candidate));
    }

    private BigDecimal rawDistance(Candidate candidate) {
        return BigDecimal.valueOf(candidate.rawDistanceKm());
    }

    private record Candidate(
            double rawDistanceKm,
            boolean hasOrigin,
            BranchSnapshot branch,
            BranchAvailabilityDecisionSnapshot decision,
            Integer queueWaitEstimateMin
    ) {
    }
}
