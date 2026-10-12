package com.carwash.marketplace.domain;

import com.carwash.shared.domain.Repository;

import java.util.Optional;

public interface CarWashBusinessRepository extends Repository<CarWashBusiness, String>, com.carwash.shared.application.PagedRepository<CarWashBusiness> {
    boolean existsByRegistrationNumberIgnoreCase(String registrationNumber, String excludedBusinessId);
    Optional<CarWashBusiness> findByIdAndTenantId(String businessId, String tenantId);
    boolean updateForTenant(CarWashBusiness business, String tenantId);
    boolean updateForAdministrator(CarWashBusiness business);
    default java.util.List<CarWashBusiness> findByIds(java.util.Collection<String> ids) { return ids.stream().map(this::findById).flatMap(java.util.Optional::stream).toList(); }
}
