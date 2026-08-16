package com.carwash.catalog.domain;

import com.carwash.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceOfferingTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 8, 0);

    @Test
    void validCreationStartsWithProvidedLifecycleAndTerms() {
        ServiceOffering offering = offering(BigDecimal.valueOf(125.50), 45, 3);

        assertEquals("offering-001", offering.getOfferingId());
        assertEquals("branch-001", offering.getBranchId());
        assertEquals("service-001", offering.getServiceId());
        assertEquals(BigDecimal.valueOf(125.50), offering.getPrice());
        assertEquals(45, offering.getEstimatedDurationMin());
        assertEquals(3, offering.getConcurrentCapacity());
        assertTrue(offering.isActive());
    }

    @Test
    void updatesTermsWithoutChangingIdentityOwnershipLifecycleOrCreationTime() {
        ServiceOffering original = offering(BigDecimal.TEN, 30, 2);
        ServiceOffering updated = original.updateTerms(BigDecimal.valueOf(99.99), 60, 5, NOW.plusHours(1));

        assertEquals(original.getOfferingId(), updated.getOfferingId());
        assertEquals(original.getBranchId(), updated.getBranchId());
        assertEquals(original.getServiceId(), updated.getServiceId());
        assertEquals(original.getStatus(), updated.getStatus());
        assertEquals(original.getCreatedAt(), updated.getCreatedAt());
        assertEquals(NOW.plusHours(1), updated.getUpdatedAt());
        assertEquals(BigDecimal.valueOf(99.99), updated.getPrice());
        assertEquals(60, updated.getEstimatedDurationMin());
        assertEquals(5, updated.getConcurrentCapacity());
    }

    @Test
    void activationAndDeactivationRetainThePermanentRelationship() {
        ServiceOffering inactive = offering(BigDecimal.ZERO, 1, 1).deactivate(NOW.plusMinutes(1));
        assertFalse(inactive.isActive());
        assertEquals("branch-001", inactive.getBranchId());
        assertEquals("service-001", inactive.getServiceId());

        ServiceOffering active = inactive.activate(NOW.plusMinutes(2));
        assertTrue(active.isActive());
        assertEquals(inactive.getOfferingId(), active.getOfferingId());
        assertEquals(inactive.getCreatedAt(), active.getCreatedAt());
    }

    @Test
    void rejectsInvalidIdentityPriceDurationCapacityAndMetadataBoundaries() {
        assertThrows(BusinessRuleViolationException.class,
                () -> new ServiceOffering(" ", "branch", "service", BigDecimal.ZERO, 1, 1,
                        ServiceOfferingStatus.ACTIVE, NOW, NOW));
        assertThrows(BusinessRuleViolationException.class,
                () -> new ServiceOffering("offering", "b".repeat(65), "service", BigDecimal.ZERO, 1, 1,
                        ServiceOfferingStatus.ACTIVE, NOW, NOW));
        assertThrows(BusinessRuleViolationException.class, () -> offering(BigDecimal.valueOf(-0.01), 30, 1));
        assertThrows(BusinessRuleViolationException.class, () -> offering(new BigDecimal("1.001"), 30, 1));
        assertThrows(BusinessRuleViolationException.class,
                () -> offering(new BigDecimal("10000000000.00"), 30, 1));
        assertThrows(BusinessRuleViolationException.class, () -> offering(BigDecimal.ZERO, 0, 1));
        assertThrows(BusinessRuleViolationException.class, () -> offering(BigDecimal.ZERO, 1441, 1));
        assertThrows(BusinessRuleViolationException.class, () -> offering(BigDecimal.ZERO, 1, 0));
        assertThrows(BusinessRuleViolationException.class, () -> offering(BigDecimal.ZERO, 1, 1001));
    }

    private ServiceOffering offering(BigDecimal price, int duration, int capacity) {
        return new ServiceOffering(
                "offering-001", "branch-001", "service-001", price, duration, capacity,
                ServiceOfferingStatus.ACTIVE, NOW, NOW);
    }
}
