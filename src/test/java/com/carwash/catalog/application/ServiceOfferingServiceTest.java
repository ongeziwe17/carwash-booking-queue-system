package com.carwash.catalog.application;

import com.carwash.catalog.domain.Service;
import com.carwash.catalog.domain.ServiceOfferingStatus;
import com.carwash.catalog.infrastructure.InMemoryServiceOfferingRepository;
import com.carwash.catalog.infrastructure.InMemoryServiceRepository;
import com.carwash.marketplace.application.CreateBranchCommand;
import com.carwash.marketplace.application.MarketplaceManagementService;
import com.carwash.marketplace.application.RegisterBusinessCommand;
import com.carwash.marketplace.application.UpdateBranchCommand;
import com.carwash.marketplace.infrastructure.InMemoryCarWashBranchRepository;
import com.carwash.marketplace.infrastructure.InMemoryCarWashBusinessRepository;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceOfferingServiceTest {

    private InMemoryServiceOfferingRepository offerings;
    private InMemoryServiceRepository services;
    private MarketplaceManagementService marketplace;
    private ServiceOfferingService offeringService;

    @BeforeEach
    void setUp() {
        InMemoryDataCoordinator coordinator = new InMemoryDataCoordinator();
        Clock clock = Clock.fixed(Instant.parse("2030-01-01T08:00:00Z"), ZoneOffset.UTC);
        offerings = new InMemoryServiceOfferingRepository();
        services = new InMemoryServiceRepository();
        marketplace = new MarketplaceManagementService(
                new InMemoryCarWashBusinessRepository(),
                new InMemoryCarWashBranchRepository(),
                coordinator,
                clock);
        offeringService = new ServiceOfferingService(offerings, services, marketplace, coordinator, clock);
        marketplace.registerBusiness(business("business-001"));
        marketplace.createBranch("business-001", branch("branch-001", true));
        services.insert(service("service-001"));
    }

    @Test
    void createsActiveOfferingAndPublishesDetachedLookupSnapshots() {
        ServiceOfferingSnapshot created = offeringService.createOffering(
                "branch-001", command("offering-001", "service-001", "125.50", 45, 3));

        assertEquals(ServiceOfferingStatus.ACTIVE, created.status());
        assertTrue(created.effectiveActive());
        assertTrue(created.discoverable());
        assertEquals("Exterior Wash", created.serviceName());
        assertEquals(3, created.concurrentCapacity());
        assertEquals(created, offeringService.findOffering("offering-001"));
        assertEquals(created, offeringService.findOfferingOptional("offering-001").orElseThrow());
        assertEquals(created, offeringService.findOfferingByBranchAndService("branch-001", "service-001")
                .orElseThrow());
    }

    @Test
    void twoBranchesCanOfferTheSameServiceWithIndependentTermsAndDeterministicOrdering() {
        marketplace.createBranch("business-001", branch("branch-002", true));
        offeringService.createOffering(
                "branch-001", command("offering-b", "service-001", "100.00", 30, 2));
        ServiceOfferingSnapshot second = offeringService.createOffering(
                "branch-002", command("offering-a", "service-001", "175.00", 60, 5));
        services.insert(service("service-002"));
        offeringService.createOffering(
                "branch-001", command("offering-a", "service-002", "50.00", 15, 1));

        List<ServiceOfferingSnapshot> firstBranch = offeringService.findOfferingsByBranch("branch-001");
        assertEquals(List.of("offering-a", "offering-b"), firstBranch.stream()
                .map(ServiceOfferingSnapshot::offeringId).toList());
        assertEquals(new BigDecimal("100.00"), firstBranch.get(1).price());
        assertEquals(new BigDecimal("175.00"), second.price());
        assertEquals(2, firstBranch.get(1).concurrentCapacity());
        assertEquals(5, second.concurrentCapacity());
    }

    @Test
    void updateAndLifecyclePreserveIdentityAndControlDiscovery() {
        ServiceOfferingSnapshot created = offeringService.createOffering(
                "branch-001", command("offering-001", "service-001", "100.00", 30, 2));
        ServiceOfferingSnapshot updated = offeringService.updateOffering(
                "offering-001", new UpdateServiceOfferingCommand(new BigDecimal("120.00"), 40, 4));

        assertEquals(created.offeringId(), updated.offeringId());
        assertEquals(created.branchId(), updated.branchId());
        assertEquals(created.serviceId(), updated.serviceId());
        assertEquals(created.createdAt(), updated.createdAt());
        assertEquals(new BigDecimal("120.00"), updated.price());
        assertFalse(offeringService.deactivateOffering("offering-001").discoverable());
        assertTrue(offeringService.findDiscoverableOfferingsByBranch("branch-001").isEmpty());
        assertTrue(offeringService.activateOffering("offering-001").discoverable());
    }

    @Test
    void parentLifecycleAndPublicDiscoveryAreDerivedWithoutRewritingOfferingStatus() {
        offeringService.createOffering(
                "branch-001", command("offering-001", "service-001", "100.00", 30, 2));

        services.findById("service-001").orElseThrow().deactivate();
        assertIneffectiveButStoredActive();
        services.findById("service-001").orElseThrow().activate();
        assertTrue(offeringService.findOffering("offering-001").discoverable());

        marketplace.deactivateBranch("branch-001");
        assertIneffectiveButStoredActive();
        marketplace.activateBranch("branch-001");
        assertTrue(offeringService.findOffering("offering-001").discoverable());

        marketplace.deactivateBusiness("business-001");
        assertIneffectiveButStoredActive();
        marketplace.activateBusiness("business-001");
        assertTrue(offeringService.findOffering("offering-001").discoverable());

        marketplace.updateBranch("branch-001", updateBranch(false));
        ServiceOfferingSnapshot privateBranch = offeringService.findOffering("offering-001");
        assertTrue(privateBranch.effectiveActive());
        assertFalse(privateBranch.discoverable());
        assertEquals(ServiceOfferingStatus.ACTIVE, privateBranch.status());
        marketplace.updateBranch("branch-001", updateBranch(true));
        assertTrue(offeringService.findOffering("offering-001").discoverable());
    }

    @Test
    void duplicateIdentifiersRelationshipsAndUnknownReferencesAreRejected() {
        offeringService.createOffering(
                "branch-001", command("offering-001", "service-001", "100.00", 30, 2));
        marketplace.createBranch("business-001", branch("branch-002", true));

        assertThrows(BusinessRuleViolationException.class, () -> offeringService.createOffering(
                "branch-002", command("offering-001", "service-001", "90.00", 20, 1)));
        offeringService.deactivateOffering("offering-001");
        assertThrows(BusinessRuleViolationException.class, () -> offeringService.createOffering(
                "branch-001", command("offering-002", "service-001", "90.00", 20, 1)));
        assertThrows(ResourceNotFoundException.class, () -> offeringService.createOffering(
                "missing", command("offering-003", "service-001", "90.00", 20, 1)));
        assertThrows(ResourceNotFoundException.class, () -> offeringService.createOffering(
                "branch-001", command("offering-003", "missing", "90.00", 20, 1)));
        assertThrows(ResourceNotFoundException.class, () -> offeringService.findOffering("missing"));
    }

    private void assertIneffectiveButStoredActive() {
        ServiceOfferingSnapshot snapshot = offeringService.findOffering("offering-001");
        assertEquals(ServiceOfferingStatus.ACTIVE, snapshot.status());
        assertFalse(snapshot.effectiveActive());
        assertFalse(snapshot.discoverable());
        assertTrue(offeringService.findDiscoverableOfferingsByBranch("branch-001").isEmpty());
    }

    private Service service(String id) {
        return new Service(id, "Exterior Wash", "Reusable wash definition", BigDecimal.TEN, 20);
    }

    private RegisterBusinessCommand business(String id) {
        return new RegisterBusinessCommand(
                id, "Wash Group", "info@example.test", "+27 82 123 4567", "REG-001");
    }

    private CreateBranchCommand branch(String id, boolean publicDiscovery) {
        return new CreateBranchCommand(
                id, "City Bowl", "1 Main Road", null, "Cape Town", "Western Cape", "8001", "ZA",
                BigDecimal.valueOf(-33.9249), BigDecimal.valueOf(18.4241), "Africa/Johannesburg", publicDiscovery);
    }

    private UpdateBranchCommand updateBranch(boolean publicDiscovery) {
        return new UpdateBranchCommand(
                "City Bowl", "1 Main Road", null, "Cape Town", "Western Cape", "8001", "ZA",
                BigDecimal.valueOf(-33.9249), BigDecimal.valueOf(18.4241), "Africa/Johannesburg", publicDiscovery);
    }

    private CreateServiceOfferingCommand command(
            String offeringId,
            String serviceId,
            String price,
            int duration,
            int capacity
    ) {
        return new CreateServiceOfferingCommand(
                offeringId, serviceId, new BigDecimal(price), duration, capacity);
    }
}
