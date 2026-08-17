package com.carwash.discovery.application;

import com.carwash.catalog.application.ServiceDefinitionQuery;
import com.carwash.catalog.application.ServiceDefinitionSnapshot;
import com.carwash.catalog.application.ServiceOfferingQuery;
import com.carwash.catalog.application.ServiceOfferingSnapshot;
import com.carwash.discovery.domain.GeoCoordinate;
import com.carwash.marketplace.application.BranchScheduleQuery;
import com.carwash.marketplace.application.BranchSnapshot;
import com.carwash.marketplace.application.MarketplaceQuery;
import com.carwash.shared.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class NearbyBranchDiscoveryService {

    public static final int RESPONSE_DISTANCE_SCALE = 2;
    public static final RoundingMode RESPONSE_DISTANCE_ROUNDING = RoundingMode.HALF_UP;

    private final MarketplaceQuery marketplaceQuery;
    private final BranchScheduleQuery branchScheduleQuery;
    private final ServiceOfferingQuery serviceOfferingQuery;
    private final ServiceDefinitionQuery serviceDefinitionQuery;
    private final DistanceCalculator distanceCalculator;

    public NearbyBranchDiscoveryService(
            MarketplaceQuery marketplaceQuery,
            BranchScheduleQuery branchScheduleQuery,
            ServiceOfferingQuery serviceOfferingQuery,
            ServiceDefinitionQuery serviceDefinitionQuery,
            DistanceCalculator distanceCalculator
    ) {
        this.marketplaceQuery = Objects.requireNonNull(marketplaceQuery, "Marketplace query is required");
        this.branchScheduleQuery = Objects.requireNonNull(branchScheduleQuery, "Branch schedule query is required");
        this.serviceOfferingQuery = Objects.requireNonNull(serviceOfferingQuery, "Service offering query is required");
        this.serviceDefinitionQuery = Objects.requireNonNull(serviceDefinitionQuery,
                "Service definition query is required");
        this.distanceCalculator = Objects.requireNonNull(distanceCalculator, "Distance calculator is required");
    }

    public List<NearbyBranchSnapshot> findNearby(NearbyBranchSearchCriteria criteria) {
        Objects.requireNonNull(criteria, "Nearby search criteria are required");
        GeoCoordinate origin = criteria.origin();
        ServiceDefinitionSnapshot selectedService = requireService(criteria.serviceId());
        if (selectedService != null && !selectedService.active()) {
            return List.of();
        }

        List<Candidate> candidates = new ArrayList<>();
        for (BranchSnapshot branch : marketplaceQuery.findDiscoverableBranches()) {
            if (!branch.discoverable() || !branch.effectiveActive() || !branch.publicDiscoveryEnabled()) {
                continue;
            }
            GeoCoordinate destination = validStoredCoordinate(branch);
            if (destination == null) {
                continue;
            }
            double rawDistanceKm = distanceCalculator.distanceKm(origin, destination);
            if (!Double.isFinite(rawDistanceKm) || rawDistanceKm < 0.0d) {
                continue;
            }
            if (criteria.radiusKm() != null
                    && Double.compare(rawDistanceKm, criteria.radiusKm().doubleValue()) > 0) {
                continue;
            }
            if (selectedService != null && !hasDiscoverableOffering(branch, selectedService.serviceId())) {
                continue;
            }
            if (criteria.openAt() != null
                    && !branchScheduleQuery.getOpenStatus(branch.branchId(), criteria.openAt()).open()) {
                continue;
            }
            candidates.add(new Candidate(rawDistanceKm, snapshot(branch, rawDistanceKm)));
        }

        Comparator<Candidate> order = criteria.sort() == NearbyBranchSort.DISTANCE
                ? Comparator.comparingDouble(Candidate::rawDistanceKm)
                        .thenComparing(candidate -> candidate.snapshot().branchId())
                : Comparator.comparing(candidate -> candidate.snapshot().branchId());
        return candidates.stream().sorted(order).map(Candidate::snapshot).toList();
    }

    private ServiceDefinitionSnapshot requireService(String serviceId) {
        if (serviceId == null) {
            return null;
        }
        return serviceDefinitionQuery.findServiceDefinitionOptional(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));
    }

    private boolean hasDiscoverableOffering(BranchSnapshot branch, String serviceId) {
        return serviceOfferingQuery.findOfferingByBranchAndService(branch.branchId(), serviceId)
                .filter(offering -> canonicalDiscoverableOffering(offering, branch, serviceId))
                .isPresent();
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

    private NearbyBranchSnapshot snapshot(BranchSnapshot branch, double rawDistanceKm) {
        return new NearbyBranchSnapshot(
                branch.branchId(),
                branch.businessId(),
                branch.branchName(),
                branch.addressLine1(),
                branch.addressLine2(),
                branch.city(),
                branch.province(),
                branch.postalCode(),
                branch.countryCode(),
                branch.latitude(),
                branch.longitude(),
                branch.timezone(),
                BigDecimal.valueOf(rawDistanceKm).setScale(RESPONSE_DISTANCE_SCALE, RESPONSE_DISTANCE_ROUNDING)
        );
    }

    private record Candidate(double rawDistanceKm, NearbyBranchSnapshot snapshot) {
    }
}
