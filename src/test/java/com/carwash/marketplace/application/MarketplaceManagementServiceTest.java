package com.carwash.marketplace.application;

import com.carwash.access.application.TenantAccessContext;
import com.carwash.identity.domain.RoleName;
import com.carwash.marketplace.domain.BranchStatus;
import com.carwash.marketplace.domain.BusinessStatus;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceManagementServiceTest {

    private InMemoryCarWashBusinessRepository businesses;
    private InMemoryCarWashBranchRepository branches;
    private MarketplaceManagementService marketplace;

    @BeforeEach
    void setUp() {
        businesses = new InMemoryCarWashBusinessRepository();
        branches = new InMemoryCarWashBranchRepository();
        Clock clock = Clock.fixed(Instant.parse("2030-01-01T06:00:00Z"), ZoneId.of("Africa/Johannesburg"));
        marketplace = new MarketplaceManagementService(
                businesses, branches, new InMemoryDataCoordinator(), clock);
    }

    @Test
    void registersAndRetrievesNormalizedBusiness() {
        BusinessSnapshot created = marketplace.registerBusiness(new RegisterBusinessCommand(
                " business-001 ", " Wash Group ", " INFO@EXAMPLE.TEST ", " +27 82 123 4567 ", "  REG-001  "));

        assertEquals("business-001", created.businessId());
        assertEquals("Wash Group", created.businessName());
        assertEquals("info@example.test", created.contactEmail());
        assertEquals(BusinessStatus.ACTIVE, created.status());
        assertEquals(created.registeredAt(), created.updatedAt());
        assertEquals(created, marketplace.findBusiness("business-001"));
        assertEquals(List.of(created), marketplace.findAllBusinesses());
        assertTrue(marketplace.findBusinessOptional(" business-001 ").isPresent());
    }

    @Test
    void duplicateBusinessIdCannotOverwriteExistingRecord() {
        marketplace.registerBusiness(validBusiness("business-001"));

        BusinessRuleViolationException failure = assertThrows(BusinessRuleViolationException.class,
                () -> marketplace.registerBusiness(validBusiness("business-001")));

        assertEquals("Business ID already exists", failure.getMessage());
        assertEquals(1, businesses.findAll().size());
    }

    @Test
    void registrationNumbersRemainCaseInsensitiveAndUniqueAcrossProfiles() {
        marketplace.registerBusiness(validBusiness("business-001"));

        BusinessRuleViolationException createFailure = assertThrows(BusinessRuleViolationException.class,
                () -> marketplace.registerBusiness(new RegisterBusinessCommand(
                        "business-002", "Second Wash", "second@example.test", "+27 21 555 0101",
                        "reg-business-001")));
        assertEquals("Business registration number already exists", createFailure.getMessage());

        marketplace.registerBusiness(validBusiness("business-002"));
        BusinessRuleViolationException updateFailure = assertThrows(BusinessRuleViolationException.class,
                () -> marketplace.updateBusiness("business-002", new UpdateBusinessCommand(
                        "Second Wash", "second@example.test", "+27 21 555 0101", "REG-BUSINESS-001")));
        assertEquals("Business registration number already exists", updateFailure.getMessage());
        assertEquals("REG-business-002", marketplace.findBusiness("business-002").registrationNumber());
    }

    @Test
    void businessUpdatePreservesIdentityAndLifecycle() {
        BusinessSnapshot created = marketplace.registerBusiness(validBusiness("business-001"));
        BusinessSnapshot updated = marketplace.updateBusiness("business-001", new UpdateBusinessCommand(
                "New Name", "new@example.test", "+27 21 555 0100", ""));

        assertEquals(created.businessId(), updated.businessId());
        assertEquals(created.registeredAt(), updated.registeredAt());
        assertEquals(BusinessStatus.ACTIVE, updated.status());
        assertEquals("New Name", updated.businessName());
        assertNull(updated.registrationNumber());
    }

    @Test
    void createsBranchesWithPermanentBusinessOwnershipAndDeterministicListing() {
        marketplace.registerBusiness(validBusiness("business-001"));
        BranchSnapshot second = marketplace.createBranch("business-001", validBranch("branch-002", true));
        BranchSnapshot first = marketplace.createBranch("business-001", validBranch("branch-001", true));

        assertEquals("business-001", first.businessId());
        assertEquals("Africa/Johannesburg", first.timezone());
        assertTrue(first.effectiveActive());
        assertTrue(first.discoverable());
        assertEquals(List.of("branch-001", "branch-002"), marketplace.findBranchesByBusiness("business-001")
                .stream().map(BranchSnapshot::branchId).toList());
        assertEquals(second, marketplace.findBranch("branch-002"));
        assertTrue(marketplace.findBranchOptional(" branch-001 ").isPresent());
    }

    @Test
    void branchPagesResolveCanonicalBusinessAndKeepTenantScopedTotals() {
        marketplace.registerBusiness(validBusiness("business-001"));
        marketplace.registerBusiness(validBusiness("business-002"));
        marketplace.registerBusiness(validBusiness("business-empty"));
        marketplace.createBranch("business-001", validBranch("branch-001", true));
        marketplace.createBranch("business-001", validBranch("branch-002", true));
        marketplace.createBranch("business-002", validBranch("branch-foreign", true));
        var admin = new TenantAccessContext("admin", RoleName.PLATFORM_ADMIN, null);
        var owner = new TenantAccessContext("owner", RoleName.BUSINESS_OWNER, "business-001");
        var all = BranchListPolicy.POLICY.parse(Map.of());

        assertEquals("Business not found: missing", assertThrows(ResourceNotFoundException.class,
                () -> marketplace.findBranchesPage(admin, " missing ", all)).getMessage());
        var empty = marketplace.findBranchesPage(admin, "business-empty", all);
        assertTrue(empty.content().isEmpty());
        assertEquals(0, empty.totalElements());
        assertEquals(0, empty.page());
        assertEquals(20, empty.size());
        assertEquals(marketplace.findBranchesPage(admin, "business-001", all),
                marketplace.findBranchesPage(admin, " business-001 ", all));
        assertEquals(marketplace.findBranchesPage(owner, "business-001", all),
                marketplace.findBranchesPage(owner, " business-001 ", all));
        assertEquals(2, marketplace.findBranchesPage(owner, "business-001", all).totalElements());
        for (String inaccessible : List.of("business-002", "missing")) {
            assertEquals("Business not found", assertThrows(ResourceNotFoundException.class,
                    () -> marketplace.findBranchesPage(owner, inaccessible, all)).getMessage());
        }

        var filtered = BranchListPolicy.POLICY.parse(Map.of("city", List.of("Cape Town"),
                "status", List.of("ACTIVE"), "size", List.of("1"), "page", List.of("5"),
                "sort", List.of("name,desc", "id,asc")));
        var page = marketplace.findBranchesPage(owner, " business-001 ", filtered);
        assertTrue(page.content().isEmpty());
        assertEquals(2, page.totalElements());
        assertEquals(2, page.totalPages());
        assertTrue(page.hasPrevious());
        assertFalse(page.hasNext());
        assertEquals(0, marketplace.findBranchesPage(owner, "business-001", BranchListPolicy.POLICY.parse(
                Map.of("city", List.of("Elsewhere")))).totalElements());

        var captured = marketplace.findBranchesPage(owner, "business-001", all);
        marketplace.updateBranch("branch-001", new UpdateBranchCommand("Waterfront", "2 Dock Road", null,
                "Cape Town", "Western Cape", "8001", "ZA", BigDecimal.valueOf(-33.908),
                BigDecimal.valueOf(18.42), "Africa/Johannesburg", true));
        assertEquals("City Bowl", captured.content().getFirst().branchName());
        assertEquals("Waterfront", marketplace.findBranchesPage(owner, "business-001", all).content().getFirst().branchName());
    }

    @Test
    void duplicateBranchIdCannotMoveOrOverwriteExistingBranch() {
        marketplace.registerBusiness(validBusiness("business-001"));
        marketplace.registerBusiness(validBusiness("business-002"));
        marketplace.createBranch("business-001", validBranch("branch-001", true));

        BusinessRuleViolationException failure = assertThrows(BusinessRuleViolationException.class,
                () -> marketplace.createBranch("business-002", validBranch("branch-001", true)));

        assertEquals("Branch ID already exists", failure.getMessage());
        assertEquals("business-001", marketplace.findBranch("branch-001").businessId());
    }

    @Test
    void branchUpdatePreservesIdentityOwnershipAndStatus() {
        marketplace.registerBusiness(validBusiness("business-001"));
        BranchSnapshot created = marketplace.createBranch("business-001", validBranch("branch-001", true));
        BranchSnapshot updated = marketplace.updateBranch("branch-001", new UpdateBranchCommand(
                "Waterfront", "2 Dock Road", "Unit 5", "Cape Town", "Western Cape", "8001", "za",
                BigDecimal.valueOf(-33.908), BigDecimal.valueOf(18.42), "Africa/Johannesburg", false));

        assertEquals(created.branchId(), updated.branchId());
        assertEquals(created.businessId(), updated.businessId());
        assertEquals(created.createdAt(), updated.createdAt());
        assertEquals(BranchStatus.ACTIVE, updated.status());
        assertEquals("ZA", updated.countryCode());
        assertFalse(updated.publicDiscoveryEnabled());
        assertFalse(updated.discoverable());
    }

    @Test
    void inactiveBusinessMakesActiveBranchesIneffectiveAndUndiscoverableWithoutMutatingThem() {
        marketplace.registerBusiness(validBusiness("business-001"));
        marketplace.createBranch("business-001", validBranch("branch-001", true));
        assertEquals(1, marketplace.findDiscoverableBranches().size());

        marketplace.deactivateBusiness("business-001");

        BranchSnapshot inactiveParentView = marketplace.findBranch("branch-001");
        assertEquals(BranchStatus.ACTIVE, inactiveParentView.status());
        assertFalse(inactiveParentView.effectiveActive());
        assertFalse(inactiveParentView.discoverable());
        assertTrue(marketplace.findDiscoverableBranches().isEmpty());

        marketplace.activateBusiness("business-001");
        assertTrue(marketplace.findBranch("branch-001").discoverable());
    }

    @Test
    void inactiveOrPrivateBranchesAreExcludedFromDiscovery() {
        marketplace.registerBusiness(validBusiness("business-001"));
        marketplace.createBranch("business-001", validBranch("branch-public", true));
        marketplace.createBranch("business-001", validBranch("branch-private", false));
        marketplace.createBranch("business-001", validBranch("branch-inactive", true));
        marketplace.deactivateBranch("branch-inactive");

        assertEquals(List.of("branch-public"), marketplace.findDiscoverableBranches().stream()
                .map(BranchSnapshot::branchId).toList());
        assertEquals(BranchStatus.INACTIVE, marketplace.findBranch("branch-inactive").status());
        assertTrue(marketplace.activateBranch("branch-inactive").discoverable());
    }

    @Test
    void invalidCoordinatesAndTimezoneAreRejectedBeforeInsert() {
        marketplace.registerBusiness(validBusiness("business-001"));

        assertThrows(BusinessRuleViolationException.class, () -> marketplace.createBranch(
                "business-001", branch("branch-lat", BigDecimal.valueOf(90.1), BigDecimal.ZERO,
                        "Africa/Johannesburg")));
        assertThrows(BusinessRuleViolationException.class, () -> marketplace.createBranch(
                "business-001", branch("branch-lon", BigDecimal.ZERO, BigDecimal.valueOf(-180.1),
                        "Africa/Johannesburg")));
        BusinessRuleViolationException timezoneFailure = assertThrows(BusinessRuleViolationException.class,
                () -> marketplace.createBranch("business-001", branch(
                        "branch-zone", BigDecimal.ZERO, BigDecimal.ZERO, "Africa/Unknown")));

        assertEquals("Timezone identifier is invalid", timezoneFailure.getMessage());
        assertTrue(branches.findAll().isEmpty());
    }

    @Test
    void applicationLayerRejectsInvalidBusinessFieldsForInternalCallers() {
        assertThrows(BusinessRuleViolationException.class, () -> marketplace.registerBusiness(null));
        assertThrows(BusinessRuleViolationException.class, () -> marketplace.registerBusiness(
                new RegisterBusinessCommand("", "Name", "valid@example.test", "+27 82 123 4567", null)));
        assertThrows(BusinessRuleViolationException.class, () -> marketplace.registerBusiness(
                new RegisterBusinessCommand("business-1", "Name", "invalid", "+27 82 123 4567", null)));
        assertThrows(BusinessRuleViolationException.class, () -> marketplace.registerBusiness(
                new RegisterBusinessCommand("business-1", "Name", "valid@example.test", "phone", null)));
    }

    @Test
    void missingBusinessesAndBranchesUseResourceNotFoundContract() {
        assertThrows(ResourceNotFoundException.class, () -> marketplace.findBusiness("missing"));
        assertThrows(ResourceNotFoundException.class, () -> marketplace.findBranch("missing"));
        assertThrows(ResourceNotFoundException.class,
                () -> marketplace.createBranch("missing", validBranch("branch-001", true)));
        assertFalse(marketplace.findBusinessOptional("missing").isPresent());
        assertFalse(marketplace.findBranchOptional("missing").isPresent());
    }

    @Test
    void snapshotsContainBoundedOnboardingAndLocationMetadata() {
        BusinessSnapshot business = marketplace.registerBusiness(validBusiness("business-001"));
        BranchSnapshot branch = marketplace.createBranch("business-001", validBranch("branch-001", true));

        assertNotNull(business.registeredAt());
        assertNotNull(business.updatedAt());
        assertEquals("REG-business-001", business.registrationNumber());
        assertEquals("1 Main Road", branch.addressLine1());
        assertNull(branch.addressLine2());
        assertEquals(BigDecimal.valueOf(-33.9249), branch.latitude());
        assertNotNull(branch.updatedAt());
    }

    private RegisterBusinessCommand validBusiness(String businessId) {
        return new RegisterBusinessCommand(
                businessId, "Wash Group", "info@example.test", "+27 82 123 4567", "REG-" + businessId);
    }

    private CreateBranchCommand validBranch(String branchId, boolean discoverable) {
        return new CreateBranchCommand(
                branchId, "City Bowl", "1 Main Road", null, "Cape Town", "Western Cape", "8001", "za",
                BigDecimal.valueOf(-33.9249), BigDecimal.valueOf(18.4241), "Africa/Johannesburg", discoverable);
    }

    private CreateBranchCommand branch(
            String branchId,
            BigDecimal latitude,
            BigDecimal longitude,
            String timezone
    ) {
        return new CreateBranchCommand(
                branchId, "City Bowl", "1 Main Road", null, "Cape Town", "Western Cape", "8001", "ZA",
                latitude, longitude, timezone, true);
    }
}
