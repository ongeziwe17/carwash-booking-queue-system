package com.carwash.identity.application;

import com.carwash.identity.domain.RoleCatalog;
import com.carwash.identity.domain.RoleName;
import com.carwash.identity.domain.TenantMembership;
import com.carwash.identity.domain.User;
import com.carwash.identity.infrastructure.InMemoryTenantMembershipRepository;
import com.carwash.identity.infrastructure.InMemoryUserRepository;
import com.carwash.shared.application.MutationLock;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantMembershipManagementServiceTest {

    private InMemoryUserRepository users;
    private InMemoryTenantMembershipRepository memberships;
    private TenantMembershipManagementService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        memberships = new InMemoryTenantMembershipRepository();
        service = new TenantMembershipManagementService(
                users,
                memberships,
                businessId -> Set.of("business-a", "business-b").contains(businessId),
                new InMemoryDataCoordinator(),
                MutationLock.noOp(),
                Clock.fixed(Instant.parse("2026-08-26T08:00:00Z"), ZoneOffset.UTC)
        );
        assertTrue(users.insert(customer("operator")));
    }

    @Test
    void operationalRoleAndMembershipAreAssignedAndReplacedTogether() {
        User staff = service.assignRole("operator", RoleName.STAFF, "business-a");
        assertEquals(RoleName.STAFF, RoleCatalog.name(staff.getRole()));
        assertEquals("business-a", service.findByUserId("operator").orElseThrow().businessId());

        TenantMembership replacement = service.assignOrReplace("operator", "business-b");
        assertEquals("business-b", replacement.businessId());
        assertEquals(1, memberships.findAll().size());
        assertEquals("business-b", memberships.findById("operator").orElseThrow().businessId());
    }

    @Test
    void nonOperationalRolesCannotCarryMembershipAndRemovalDemotesAtomically() {
        assertThrows(BusinessRuleViolationException.class,
                () -> service.assignRole("operator", RoleName.CUSTOMER, "business-a"));
        service.assignRole("operator", RoleName.BUSINESS_OWNER, "business-a");

        User demoted = service.removeAndDemote("operator");
        assertEquals(RoleName.CUSTOMER, RoleCatalog.name(demoted.getRole()));
        assertTrue(service.findByUserId("operator").isEmpty());
    }

    @Test
    void unknownBusinessAndUnassignedOperationalUserFailClosed() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.assignRole("operator", RoleName.STAFF, "missing-business"));
        assertEquals(RoleName.CUSTOMER,
                RoleCatalog.name(users.findById("operator").orElseThrow().getRole()));
        assertFalse(memberships.existsById("operator"));

        users.findById("operator").orElseThrow().setRole(RoleCatalog.role(RoleName.STAFF));
        assertTrue(users.update(users.findById("operator").orElseThrow()));
        assertThrows(ResourceNotFoundException.class,
                () -> service.assignOrReplace("operator", "missing-business"));
        assertFalse(memberships.existsById("operator"));
    }

    private User customer(String id) {
        return User.withEncodedPassword(
                id,
                "Tenant Operator",
                id + "@example.test",
                "+27821234567",
                "encoded",
                RoleCatalog.role(RoleName.CUSTOMER)
        );
    }
}
