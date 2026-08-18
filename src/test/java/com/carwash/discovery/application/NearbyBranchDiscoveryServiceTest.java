package com.carwash.discovery.application;

import com.carwash.catalog.application.ServiceDefinitionQuery;
import com.carwash.catalog.application.ServiceDefinitionSnapshot;
import com.carwash.catalog.application.ServiceOfferingQuery;
import com.carwash.catalog.application.ServiceOfferingSnapshot;
import com.carwash.catalog.domain.ServiceOfferingStatus;
import com.carwash.marketplace.application.BranchOpenStatusSnapshot;
import com.carwash.marketplace.application.BranchScheduleQuery;
import com.carwash.marketplace.application.BranchServiceWindowSnapshot;
import com.carwash.marketplace.application.BranchSnapshot;
import com.carwash.marketplace.application.BusinessSnapshot;
import com.carwash.marketplace.application.MarketplaceQuery;
import com.carwash.marketplace.domain.BranchStatus;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NearbyBranchDiscoveryServiceTest {

    private static final Instant OPEN_AT = Instant.parse("2030-01-07T08:00:00Z");

    @Test
    void defaultDiscoveryExcludesInactivePrivateAndInvalidStoredCoordinatesInBranchIdOrder() {
        BranchSnapshot branchB = branch("branch-b", true, true, "-33.93", "18.43");
        BranchSnapshot branchA = branch("branch-a", true, true, "-33.92", "18.42");
        BranchSnapshot inactive = branch("branch-inactive", false, true, "-33.91", "18.41");
        BranchSnapshot privateBranch = branch("branch-private", true, false, "-33.90", "18.40");
        BranchSnapshot invalid = branch("branch-invalid", true, true, "91", "18.40");
        NearbyBranchDiscoveryService discovery = service(
                List.of(branchB, privateBranch, invalid, inactive, branchA),
                Map.of(),
                Map.of(),
                Map.of(),
                (origin, destination) -> Math.abs(destination.latitude()));

        List<NearbyBranchSnapshot> result = discovery.findNearby(criteria(null, null, null, null));

        assertEquals(List.of("branch-a", "branch-b"), result.stream().map(NearbyBranchSnapshot::branchId).toList());
    }

    @Test
    void radiusFilteringUsesRawDistanceWhileResponseRoundsHalfUp() {
        BranchSnapshot branch = branch("branch-a", true, true, "-33.92", "18.42");
        NearbyBranchDiscoveryService excluded = service(
                List.of(branch), Map.of(), Map.of(), Map.of(), (origin, destination) -> 10.004d);
        NearbyBranchDiscoveryService included = service(
                List.of(branch), Map.of(), Map.of(), Map.of(), (origin, destination) -> 10.005d);

        assertEquals(List.of(), excluded.findNearby(criteria("10.001", null, null, null)));
        NearbyBranchSnapshot boundary = included.findNearby(criteria("10.005", null, null, null)).getFirst();
        assertEquals(new BigDecimal("10.01"), boundary.distanceKm());
    }

    @Test
    void distanceSortUsesRawValueAndBranchIdTieBreaker() {
        List<BranchSnapshot> branches = List.of(
                branch("branch-c", true, true, "3", "0"),
                branch("branch-b", true, true, "2", "0"),
                branch("branch-a", true, true, "2", "0"));
        NearbyBranchDiscoveryService discovery = service(
                branches, Map.of(), Map.of(), Map.of(), (origin, destination) -> destination.latitude());

        List<NearbyBranchSnapshot> result = discovery.findNearby(
                criteria(null, null, null, NearbyBranchSort.DISTANCE));

        assertEquals(List.of("branch-a", "branch-b", "branch-c"),
                result.stream().map(NearbyBranchSnapshot::branchId).toList());
    }

    @Test
    void serviceFilterRequiresKnownActiveServiceAndCanonicalDiscoverableOffering() {
        List<BranchSnapshot> branches = List.of(
                branch("branch-a", true, true, "1", "0"),
                branch("branch-b", true, true, "2", "0"),
                branch("branch-c", true, true, "3", "0"));
        Map<String, ServiceDefinitionSnapshot> services = Map.of(
                "service-active", definition("service-active", true),
                "service-inactive", definition("service-inactive", false),
                "service-empty", definition("service-empty", true));
        Map<String, ServiceOfferingSnapshot> offerings = Map.of(
                key("branch-a", "service-active"), offering("branch-a", "service-active", true, true),
                key("branch-b", "service-active"), offering("branch-b", "service-active", false, false),
                key("branch-c", "service-active"), offering("other-branch", "service-active", true, true));
        NearbyBranchDiscoveryService discovery = service(
                branches, services, offerings, Map.of(), (origin, destination) -> destination.latitude());

        assertEquals(List.of("branch-a"), discovery.findNearby(
                        criteria(null, "service-active", null, null)).stream()
                .map(NearbyBranchSnapshot::branchId).toList());
        assertEquals(List.of(), discovery.findNearby(criteria(null, "service-empty", null, null)));
        assertEquals(List.of(), discovery.findNearby(criteria(null, "service-inactive", null, null)));
        assertThrows(ResourceNotFoundException.class,
                () -> discovery.findNearby(criteria(null, "service-missing", null, null)));
    }

    @Test
    void openAtFilterUsesPublishedScheduleDecisionIncludingTemporaryClosureResult() {
        List<BranchSnapshot> branches = List.of(
                branch("branch-open", true, true, "1", "0"),
                branch("branch-closed", true, true, "2", "0"),
                branch("branch-closure", true, true, "3", "0"));
        Map<String, Boolean> open = Map.of(
                "branch-open", true,
                "branch-closed", false,
                "branch-closure", false);
        NearbyBranchDiscoveryService discovery = service(
                branches, Map.of(), Map.of(), open, (origin, destination) -> destination.latitude());

        assertEquals(List.of("branch-open"), discovery.findNearby(
                        criteria(null, null, OPEN_AT, null)).stream()
                .map(NearbyBranchSnapshot::branchId).toList());
    }

    @Test
    void criteriaEnforcesCoordinateRadiusIdentifierAndSortBounds() {
        assertThrows(BusinessRuleViolationException.class,
                () -> new NearbyBranchSearchCriteria(null, BigDecimal.ZERO, null, null, null, null));
        assertThrows(BusinessRuleViolationException.class,
                () -> new NearbyBranchSearchCriteria(BigDecimal.valueOf(91), BigDecimal.ZERO, null, null, null, null));
        assertThrows(BusinessRuleViolationException.class,
                () -> criteria("0", null, null, null));
        assertThrows(BusinessRuleViolationException.class,
                () -> criteria("20000.01", null, null, null));
        assertThrows(BusinessRuleViolationException.class,
                () -> criteria(null, "   ", null, null));
        assertThrows(BusinessRuleViolationException.class,
                () -> criteria(null, "x".repeat(65), null, null));
        assertThrows(BusinessRuleViolationException.class, () -> NearbyBranchSort.fromApiValue("name"));
    }

    private NearbyBranchDiscoveryService service(
            List<BranchSnapshot> branches,
            Map<String, ServiceDefinitionSnapshot> services,
            Map<String, ServiceOfferingSnapshot> offerings,
            Map<String, Boolean> open,
            DistanceCalculator distance
    ) {
        MarketplaceQuery marketplace = new MarketplaceQuery() {
            @Override public Optional<BusinessSnapshot> findBusinessOptional(String businessId) { return Optional.empty(); }
            @Override public Optional<BranchSnapshot> findBranchOptional(String branchId) { return Optional.empty(); }
            @Override public List<BranchSnapshot> findBranchesByBusiness(String businessId) { return List.of(); }
            @Override public List<BranchSnapshot> findDiscoverableBranches() { return List.copyOf(branches); }
        };
        ServiceDefinitionQuery definitions = id -> Optional.ofNullable(services.get(id));
        ServiceOfferingQuery offeringQuery = new StubOfferingQuery(offerings);
        BranchScheduleQuery schedules = new BranchScheduleQuery() {
            @Override
            public BranchOpenStatusSnapshot getOpenStatus(String branchId, Instant requestedAt) {
                return new BranchOpenStatusSnapshot(
                        branchId,
                        requestedAt,
                        "Africa/Johannesburg",
                        requestedAt.atZone(ZoneOffset.ofHours(2)),
                        true,
                        open.getOrDefault(branchId, false),
                        "branch-closure".equals(branchId),
                        open.getOrDefault(branchId, false),
                        "branch-closure".equals(branchId) ? "closure-1" : null,
                        "branch-closure".equals(branchId) ? "Maintenance" : null);
            }

            @Override
            public BranchServiceWindowSnapshot getServiceWindowStatus(
                    String branchId,
                    Instant startsAt,
                    Instant endsAt
            ) {
                throw new UnsupportedOperationException("Nearby discovery only queries instant open status");
            }
        };
        return new NearbyBranchDiscoveryService(marketplace, schedules, offeringQuery, definitions, distance);
    }

    private NearbyBranchSearchCriteria criteria(
            String radius,
            String serviceId,
            Instant openAt,
            NearbyBranchSort sort
    ) {
        return new NearbyBranchSearchCriteria(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                radius == null ? null : new BigDecimal(radius),
                serviceId,
                openAt,
                sort);
    }

    private BranchSnapshot branch(
            String branchId,
            boolean effectiveActive,
            boolean publicDiscovery,
            String latitude,
            String longitude
    ) {
        LocalDateTime timestamp = LocalDateTime.parse("2030-01-01T00:00:00");
        return new BranchSnapshot(
                branchId,
                "business-" + branchId,
                "Branch " + branchId,
                "1 Main Road",
                null,
                "Cape Town",
                "Western Cape",
                "8001",
                "ZA",
                new BigDecimal(latitude),
                new BigDecimal(longitude),
                "Africa/Johannesburg",
                effectiveActive ? BranchStatus.ACTIVE : BranchStatus.INACTIVE,
                publicDiscovery,
                effectiveActive,
                effectiveActive && publicDiscovery,
                timestamp,
                timestamp);
    }

    private ServiceDefinitionSnapshot definition(String serviceId, boolean active) {
        return new ServiceDefinitionSnapshot(
                serviceId, "Wash", "Definition", BigDecimal.TEN, 30, active,
                LocalDateTime.parse("2030-01-01T00:00:00"));
    }

    private ServiceOfferingSnapshot offering(
            String branchId,
            String serviceId,
            boolean effectiveActive,
            boolean discoverable
    ) {
        LocalDateTime timestamp = LocalDateTime.parse("2030-01-01T00:00:00");
        return new ServiceOfferingSnapshot(
                "offering-" + branchId,
                branchId,
                serviceId,
                "Wash",
                "Definition",
                BigDecimal.TEN,
                30,
                2,
                ServiceOfferingStatus.ACTIVE,
                effectiveActive,
                discoverable,
                timestamp,
                timestamp);
    }

    private String key(String branchId, String serviceId) {
        return branchId + ":" + serviceId;
    }

    private static final class StubOfferingQuery implements ServiceOfferingQuery {
        private final Map<String, ServiceOfferingSnapshot> offerings;

        private StubOfferingQuery(Map<String, ServiceOfferingSnapshot> offerings) {
            this.offerings = new HashMap<>(offerings);
        }

        @Override public Optional<ServiceOfferingSnapshot> findOfferingOptional(String offeringId) {
            return offerings.values().stream().filter(item -> item.offeringId().equals(offeringId)).findFirst();
        }

        @Override public List<ServiceOfferingSnapshot> findOfferingsByBranch(String branchId) {
            return offerings.values().stream().filter(item -> item.branchId().equals(branchId)).toList();
        }

        @Override public List<ServiceOfferingSnapshot> findDiscoverableOfferingsByBranch(String branchId) {
            return findOfferingsByBranch(branchId).stream().filter(ServiceOfferingSnapshot::discoverable).toList();
        }

        @Override public Optional<ServiceOfferingSnapshot> findOfferingByBranchAndService(
                String branchId,
                String serviceId
        ) {
            return Optional.ofNullable(offerings.get(branchId + ":" + serviceId));
        }
    }
}
