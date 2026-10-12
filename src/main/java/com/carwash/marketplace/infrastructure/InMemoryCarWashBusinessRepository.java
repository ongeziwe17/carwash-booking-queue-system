package com.carwash.marketplace.infrastructure;

import com.carwash.marketplace.domain.CarWashBusiness;
import com.carwash.marketplace.domain.CarWashBusinessRepository;
import com.carwash.shared.infrastructure.InMemoryRepository;

import java.util.Optional;

public final class InMemoryCarWashBusinessRepository extends InMemoryRepository<CarWashBusiness, String>
        implements CarWashBusinessRepository {
    @Override public com.carwash.shared.application.PageResult<CarWashBusiness> findPage(com.carwash.shared.application.ListQuery query) {
        var scope = query.scope();
        java.util.List<CarWashBusiness> source = scope.businessId() == null ? findMatching(v -> true) : findByIdAndTenantId(scope.businessId(), scope.businessId()).stream().toList();
        return query.select(source, java.util.Map.ofEntries(java.util.Map.entry("id", (java.util.function.Function<CarWashBusiness, Object>) v -> v.getBusinessId()),
                java.util.Map.entry("name", (java.util.function.Function<CarWashBusiness, Object>) v -> v.getBusinessName()),
                java.util.Map.entry("registeredAt", (java.util.function.Function<CarWashBusiness, Object>) v -> v.getRegisteredAt()),
                java.util.Map.entry("status", (java.util.function.Function<CarWashBusiness, Object>) v -> v.getStatus())));
    }


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
