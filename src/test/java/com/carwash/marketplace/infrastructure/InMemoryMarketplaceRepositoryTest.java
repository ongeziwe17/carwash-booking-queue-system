package com.carwash.marketplace.infrastructure;

import com.carwash.marketplace.domain.BranchStatus;
import com.carwash.marketplace.domain.BusinessStatus;
import com.carwash.marketplace.domain.CarWashBranch;
import com.carwash.marketplace.domain.CarWashBusiness;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryMarketplaceRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 8, 0);

    @Test
    void businessRepositoryPreservesExplicitInsertAndUpdateSemantics() {
        InMemoryCarWashBusinessRepository repository = new InMemoryCarWashBusinessRepository();
        CarWashBusiness business = business("business-b");

        assertTrue(repository.insert(business));
        assertFalse(repository.insert(business));
        assertTrue(repository.update(business.updateDetails(
                "Updated", "updated@example.test", "+27 82 123 4567", "REG-2", NOW.plusHours(1))));
        assertEquals("Updated", repository.findById("business-b").orElseThrow().getBusinessName());
        assertFalse(repository.update(business("business-missing")));
    }

    @Test
    void branchRepositoryFindsOnlyBranchesOwnedByRequestedBusinessInIdOrder() {
        InMemoryCarWashBranchRepository repository = new InMemoryCarWashBranchRepository();
        repository.insert(branch("branch-b", "business-1"));
        repository.insert(branch("branch-a", "business-1"));
        repository.insert(branch("branch-c", "business-2"));

        assertEquals(
                java.util.List.of("branch-a", "branch-b"),
                repository.findByBusinessId("business-1").stream().map(CarWashBranch::getBranchId).toList()
        );
        assertTrue(repository.findByBusinessId("business-3").isEmpty());
    }

    private CarWashBusiness business(String id) {
        return new CarWashBusiness(
                id, "Business", "contact@example.test", "+27 82 123 4567", "REG-1",
                BusinessStatus.ACTIVE, NOW, NOW
        );
    }

    private CarWashBranch branch(String branchId, String businessId) {
        return new CarWashBranch(
                branchId, businessId, "Branch", "1 Main Road", null, "Cape Town", "Western Cape",
                "8001", "ZA", BigDecimal.valueOf(-33.9249), BigDecimal.valueOf(18.4241),
                "Africa/Johannesburg", BranchStatus.ACTIVE, true, NOW, NOW
        );
    }
}
