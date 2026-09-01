package com.carwash.marketplace.domain;

import com.carwash.shared.domain.Repository;

import java.util.Optional;

public interface CarWashBusinessRepository extends Repository<CarWashBusiness, String> {
    boolean existsByRegistrationNumberIgnoreCase(String registrationNumber, String excludedBusinessId);
    Optional<CarWashBusiness> findByIdAndTenantId(String businessId, String tenantId);
    boolean updateForTenant(CarWashBusiness business, String tenantId);
    boolean updateForAdministrator(CarWashBusiness business);
}
