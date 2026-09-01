package com.carwash.marketplace.infrastructure;

import com.carwash.marketplace.domain.CarWashBusiness;
import com.carwash.marketplace.domain.CarWashBusinessRepository;
import com.carwash.shared.infrastructure.InMemoryRepository;

import java.util.Optional;

public final class InMemoryCarWashBusinessRepository extends InMemoryRepository<CarWashBusiness, String>
        implements CarWashBusinessRepository {

    @Override
    public boolean existsByRegistrationNumberIgnoreCase(String registrationNumber, String excludedBusinessId) {
        if (registrationNumber == null) {
            return false;
        }
        String normalizedRegistrationNumber = registrationNumber.trim();
        return anyMatch(business -> business.getRegistrationNumber() != null
                && business.getRegistrationNumber().trim().equalsIgnoreCase(normalizedRegistrationNumber)
                && (excludedBusinessId == null || !excludedBusinessId.equals(business.getBusinessId())));
    }

    @Override
    public Optional<CarWashBusiness> findByIdAndTenantId(String businessId, String tenantId) {
        if (businessId == null || !businessId.equals(tenantId)) return Optional.empty();
        return findById(businessId);
    }

    @Override
    public boolean updateForTenant(CarWashBusiness business, String tenantId) {
        return updateMatching(business.getBusinessId(), business,
                current -> current.getBusinessId().equals(tenantId));
    }

    @Override
    public boolean updateForAdministrator(CarWashBusiness business) {
        return updateMatching(business.getBusinessId(), business, current -> true);
    }

    @Override
    protected String getId(CarWashBusiness entity) {
        return entity.getBusinessId();
    }
}
