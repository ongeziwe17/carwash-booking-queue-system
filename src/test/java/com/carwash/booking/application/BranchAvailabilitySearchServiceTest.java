package com.carwash.booking.application;

import com.carwash.catalog.application.ServiceDefinitionQuery;
import com.carwash.catalog.application.ServiceDefinitionSnapshot;
import com.carwash.catalog.application.ServiceOfferingQuery;
import com.carwash.catalog.application.ServiceOfferingSnapshot;
import com.carwash.catalog.domain.ServiceOfferingStatus;
import com.carwash.discovery.application.DistanceCalculator;
import com.carwash.marketplace.application.BranchSnapshot;
import com.carwash.marketplace.application.BusinessSnapshot;
import com.carwash.marketplace.application.MarketplaceQuery;
import com.carwash.marketplace.domain.BranchStatus;
import com.carwash.queue.application.QueueEntrySnapshot;
import com.carwash.queue.application.QueueQuery;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BranchAvailabilitySearchServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-07T06:00:00Z");
    private static final Instant START = Instant.parse("2030-01-07T08:00:00Z");

    @Test
    void filtersLifecycleAndAvailabilityAndUsesDeterministicBranchOrder() {
        BranchSnapshot branchB = branch("branch-b", true, true, "-26");
        BranchSnapshot branchA = branch("branch-a", true, true, "-34");
        BranchSnapshot privateBranch = branch("branch-private", true, false, "-33");
        BranchSnapshot inactiveBranch = branch("branch-inactive", false, true, "-32");
        BranchAvailabilitySearchService search = service(
                List.of(branchB, privateBranch, branchA, inactiveBranch),
                (branchId, offeringId, startsAt, excluded) -> decision(branchId, offeringId,
                        !"branch-b".equals(branchId)),
                (origin, destination) -> 1.0d,
                true);

        List<BranchAvailabilitySnapshot> result = search.findAvailableBranches(criteria(null, null, null, START));

        assertEquals(List.of("branch-a"), result.stream().map(BranchAvailabilitySnapshot::branchId).toList());
        assertNull(result.getFirst().distanceKm());
        assertEquals(30, result.getFirst().queueWaitEstimateMin());
    }

    @Test
    void rawDistanceControlsRadiusAndOrderingWhileResponseIsRounded() {
        BranchSnapshot branchA = branch("branch-a", true, true, "1");
        BranchSnapshot branchB = branch("branch-b", true, true, "2");
        DistanceCalculator distance = (origin, destination) -> destination.latitude() == 1.0d ? 10.004d : 9.0d;
        BranchAvailabilitySearchService search = service(
                List.of(branchA, branchB),
                (branchId, offeringId, startsAt, excluded) -> decision(branchId, offeringId, true),
                distance,
                true);

        List<BranchAvailabilitySnapshot> radius = search.findAvailableBranches(
                criteria("0", "0", "10.00", START));
        assertEquals(List.of("branch-b"), radius.stream().map(BranchAvailabilitySnapshot::branchId).toList());
        assertEquals(new BigDecimal("9.00"), radius.getFirst().distanceKm());

        List<BranchAvailabilitySnapshot> ordered = search.findAvailableBranches(
                criteria("0", "0", null, START));
        assertEquals(List.of("branch-b", "branch-a"),
                ordered.stream().map(BranchAvailabilitySnapshot::branchId).toList());
        assertEquals(new BigDecimal("10.00"), ordered.get(1).distanceKm());

        List<BranchAvailabilityCandidateSnapshot> recommendationCandidates = search.findEligibleCandidates(
                criteria("0", "0", null, START));
        assertEquals(new BigDecimal("10.004"), recommendationCandidates.get(1).rawDistanceKm());
        assertEquals("Business business-1", recommendationCandidates.get(1).businessName());
    }

    @Test
    void queueEstimateIsNullForFutureBranchLocalDateAndCriteriaRejectsConflicts() {
        BranchAvailabilitySearchService search = service(
                List.of(branch("branch-a", true, true, "1")),
                (branchId, offeringId, startsAt, excluded) -> decision(branchId, offeringId, true),
                (origin, destination) -> 1.0d,
                true);
        BranchAvailabilitySnapshot future = search.findAvailableBranches(
                criteria(null, null, null, START.plusSeconds(24 * 60 * 60))).getFirst();
        assertNull(future.queueWaitEstimateMin());

        assertThrows(BusinessRuleViolationException.class,
                () -> criteria("0", null, null, START));
        assertThrows(BusinessRuleViolationException.class,
                () -> criteria(null, null, "5", START));
        assertThrows(BusinessRuleViolationException.class,
                () -> criteria("91", "0", null, START));
        assertThrows(BusinessRuleViolationException.class,
                () -> criteria("0", "0", "20000.01", START));
        assertThrows(BusinessRuleViolationException.class,
                () -> search.findEligibleCandidates(criteria(null, null, null, START)));
    }

    @Test
    void unknownServiceIsNotFoundAndInactiveServiceReturnsEmpty() {
        BranchSnapshot branch = branch("branch-a", true, true, "1");
        assertThrows(ResourceNotFoundException.class, () -> service(
                List.of(branch),
                (branchId, offeringId, startsAt, excluded) -> decision(branchId, offeringId, true),
                (origin, destination) -> 1.0d,
                null).findAvailableBranches(criteria(null, null, null, START)));
        assertEquals(List.of(), service(
                List.of(branch),
                (branchId, offeringId, startsAt, excluded) -> decision(branchId, offeringId, true),
                (origin, destination) -> 1.0d,
                false).findAvailableBranches(criteria(null, null, null, START)));
    }

    private BranchAvailabilitySearchService service(
            List<BranchSnapshot> branches,
            BranchAvailabilityQuery decisions,
            DistanceCalculator distances,
            Boolean serviceActive
    ) {
        MarketplaceQuery marketplace = new MarketplaceQuery() {
            public Optional<BusinessSnapshot> findBusinessOptional(String businessId) {
                return Optional.of(new BusinessSnapshot(
                        businessId, "Business " + businessId, "owner@example.test", "+27820000000", null,
                        com.carwash.marketplace.domain.BusinessStatus.ACTIVE,
                        LocalDateTime.MIN, LocalDateTime.MIN));
            }
            public Optional<BranchSnapshot> findBranchOptional(String branchId) {
                return branches.stream().filter(branch -> branch.branchId().equals(branchId)).findFirst();
            }
            public List<BranchSnapshot> findBranchesByBusiness(String businessId) { return branches; }
            public List<BranchSnapshot> findDiscoverableBranches() {
                return branches.stream().filter(BranchSnapshot::discoverable).toList();
            }
        };
        ServiceOfferingQuery offerings = new ServiceOfferingQuery() {
            public Optional<ServiceOfferingSnapshot> findOfferingOptional(String offeringId) {
                return Optional.of(offering(offeringId.replace("offering-", "branch-")));
            }
            public List<ServiceOfferingSnapshot> findOfferingsByBranch(String branchId) {
                return List.of(offering(branchId));
            }
            public List<ServiceOfferingSnapshot> findDiscoverableOfferingsByBranch(String branchId) {
                return List.of(offering(branchId));
            }
            public Optional<ServiceOfferingSnapshot> findOfferingByBranchAndService(
                    String branchId, String serviceId) {
                return Optional.of(offering(branchId));
            }
        };
        ServiceDefinitionQuery services = serviceId -> serviceActive == null
                ? Optional.empty()
                : Optional.of(new ServiceDefinitionSnapshot(
                        "service-1", "Premium Wash", "Description", BigDecimal.TEN, 20,
                        serviceActive, LocalDateTime.MIN));
        QueueQuery queues = new QueueQuery() {
            public List<QueueEntrySnapshot> findQueueEntrySnapshots() { return List.of(); }
            public List<QueueEntrySnapshot> findQueueEntrySnapshotsByBranch(String branchId) { return List.of(); }
            public int estimateWaitMinutesForNewWork(String branchId) { return 30; }
            public Optional<String> findOwnerId(String queueEntryId) { return Optional.empty(); }
            public boolean existsByServiceId(String serviceId) { return false; }
        };
        return new BranchAvailabilitySearchService(
                marketplace, offerings, services, decisions, queues, distances,
                new InMemoryDataCoordinator(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private BranchSnapshot branch(String id, boolean active, boolean publiclyDiscoverable, String latitude) {
        return new BranchSnapshot(
                id, "business-1", "Branch " + id, "1 Main Road", null, "Cape Town", "Western Cape",
                "8001", "ZA", new BigDecimal(latitude), BigDecimal.ZERO, "Africa/Johannesburg",
                active ? BranchStatus.ACTIVE : BranchStatus.INACTIVE, publiclyDiscoverable,
                active, active && publiclyDiscoverable, LocalDateTime.MIN, LocalDateTime.MIN);
    }

    private ServiceOfferingSnapshot offering(String branchId) {
        return new ServiceOfferingSnapshot(
                "offering-" + branchId.substring("branch-".length()), branchId, "service-1", "Premium Wash",
                "Description", new BigDecimal("100.00"), 30, 2, ServiceOfferingStatus.ACTIVE,
                true, true, LocalDateTime.MIN, LocalDateTime.MIN);
    }

    private BranchAvailabilityDecisionSnapshot decision(String branchId, String offeringId, boolean available) {
        OffsetDateTime localStart = START.atOffset(ZoneOffset.ofHours(2));
        return new BranchAvailabilityDecisionSnapshot(
                branchId, offeringId, "service-1", "Premium Wash", new BigDecimal("100.00"), 30,
                "Africa/Johannesburg", START, START.plusSeconds(30 * 60), localStart,
                localStart.plusMinutes(30), true, true, false, 2, 0, 2,
                available, available ? BranchAvailabilityReason.AVAILABLE : BranchAvailabilityReason.CAPACITY_FULL);
    }

    private BranchAvailabilitySearchCriteria criteria(
            String latitude,
            String longitude,
            String radius,
            Instant startsAt
    ) {
        return new BranchAvailabilitySearchCriteria(
                "service-1", startsAt,
                latitude == null ? null : new BigDecimal(latitude),
                longitude == null ? null : new BigDecimal(longitude),
                radius == null ? null : new BigDecimal(radius));
    }
}
