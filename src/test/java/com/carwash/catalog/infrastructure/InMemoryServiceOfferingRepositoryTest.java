package com.carwash.catalog.infrastructure;

import com.carwash.catalog.domain.ServiceOffering;
import com.carwash.catalog.domain.ServiceOfferingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryServiceOfferingRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 8, 0);

    @Test
    void preservesExplicitInsertAndUpdateSemantics() {
        InMemoryServiceOfferingRepository repository = new InMemoryServiceOfferingRepository();
        ServiceOffering offering = offering("offering-001", "branch-001", "service-001", BigDecimal.TEN);

        assertTrue(repository.insert(offering));
        assertFalse(repository.insert(offering));
        ServiceOffering updated = offering.updateTerms(BigDecimal.valueOf(25), 45, 4, NOW.plusHours(1));
        assertTrue(repository.update(updated));
        assertEquals(BigDecimal.valueOf(25), repository.findById("offering-001").orElseThrow().getPrice());
        assertFalse(repository.update(offering("missing", "branch-001", "service-002", BigDecimal.ONE)));
    }

    @Test
    void branchAndRelationshipLookupsAreScopedAndDeterministicallyOrdered() {
        InMemoryServiceOfferingRepository repository = new InMemoryServiceOfferingRepository();
        repository.insert(offering("offering-b", "branch-001", "service-002", BigDecimal.TEN));
        repository.insert(offering("offering-a", "branch-001", "service-001", BigDecimal.ONE));
        repository.insert(offering("offering-c", "branch-002", "service-001", BigDecimal.valueOf(20)));

        assertEquals(List.of("offering-a", "offering-b"), repository.findByBranchId("branch-001").stream()
                .map(ServiceOffering::getOfferingId).toList());
        assertEquals("offering-b", repository.findByBranchIdAndServiceId("branch-001", "service-002")
                .orElseThrow().getOfferingId());
        assertTrue(repository.findByBranchIdAndServiceId("branch-002", "service-002").isEmpty());
        assertTrue(repository.existsByServiceId("service-001"));
        assertFalse(repository.existsByServiceId("service-missing"));
    }

    @Test
    void returnedCollectionsAreDetachedAndUnmodifiableSnapshots() {
        InMemoryServiceOfferingRepository repository = new InMemoryServiceOfferingRepository();
        repository.insert(offering("offering-001", "branch-001", "service-001", BigDecimal.TEN));
        List<ServiceOffering> snapshot = repository.findByBranchId("branch-001");
        repository.insert(offering("offering-002", "branch-001", "service-002", BigDecimal.ONE));

        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class, () -> repository.findAll().clear());
    }

    private ServiceOffering offering(String id, String branchId, String serviceId, BigDecimal price) {
        return new ServiceOffering(
                id, branchId, serviceId, price, 30, 2,
                ServiceOfferingStatus.ACTIVE, NOW, NOW);
    }
}
