package com.carwash.marketplace.infrastructure;

import com.carwash.marketplace.domain.CarWashBusiness;
import com.carwash.marketplace.domain.CarWashBusinessRepository;
import com.carwash.shared.infrastructure.InMemoryRepository;

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
    protected String getId(CarWashBusiness entity) {
        return entity.getBusinessId();
    }
}
