package com.carwash.catalog.infrastructure;

import com.carwash.catalog.domain.ServiceOffering;
import com.carwash.catalog.domain.ServiceOfferingRepository;
import com.carwash.shared.infrastructure.InMemoryRepository;
import com.carwash.marketplace.application.MarketplaceQuery;

import java.util.List;
import java.util.Optional;

public final class InMemoryServiceOfferingRepository
        extends InMemoryRepository<ServiceOffering, String>
        implements ServiceOfferingRepository {
    @Override public com.carwash.shared.application.PageResult<ServiceOffering> findPage(com.carwash.shared.application.ListQuery query) {
        var scope = query.scope();
        java.util.List<ServiceOffering> source = scope.branchId() == null ? findMatching(v -> true) : findByBranchId(scope.branchId());
        source = source.stream().filter(v -> scope.branchId() == null || scope.branchId().equals(v.getBranchId())).toList();
        if (scope.businessId() != null) source = source.stream().filter(v -> branchBelongsTo(v.getBranchId(), scope.businessId())).toList();
        if (scope.discoverable()) source = source.stream().filter(v -> v.isActive() && services != null && services.findById(v.getServiceId()).map(com.carwash.catalog.domain.Service::isActive).orElse(false) && marketplace != null && marketplace.findBranchOptional(v.getBranchId()).map(com.carwash.marketplace.application.BranchSnapshot::discoverable).orElse(false)).toList();
        return query.select(source, java.util.Map.ofEntries(java.util.Map.entry("id", (java.util.function.Function<ServiceOffering, Object>) v -> v.getOfferingId()),
                java.util.Map.entry("price", (java.util.function.Function<ServiceOffering, Object>) v -> v.getPrice()),
                java.util.Map.entry("duration", (java.util.function.Function<ServiceOffering, Object>) v -> v.getEstimatedDurationMin()),
                java.util.Map.entry("status", (java.util.function.Function<ServiceOffering, Object>) v -> v.getStatus()),
                java.util.Map.entry("serviceId", (java.util.function.Function<ServiceOffering, Object>) v -> v.getServiceId())));
    }


    private final MarketplaceQuery marketplace;
    private final com.carwash.catalog.domain.ServiceRepository services;

    public InMemoryServiceOfferingRepository() {
        this(null);
    }

    public InMemoryServiceOfferingRepository(MarketplaceQuery marketplace) {
        this(marketplace, null);
    }

    public InMemoryServiceOfferingRepository(MarketplaceQuery marketplace, com.carwash.catalog.domain.ServiceRepository services) {
        this.marketplace = marketplace;
        this.services = services;
    }

    @Override
    protected String getId(ServiceOffering entity) {
        return entity.getOfferingId();
    }

    @Override
    public List<ServiceOffering> findByBranchId(String branchId) {
        return findMatching(offering -> branchId.equals(offering.getBranchId()));
    }

    @Override
    public List<ServiceOffering> findByServiceId(String serviceId) {
        return findMatching(offering -> serviceId.equals(offering.getServiceId()));
    }

    @Override
    public Optional<ServiceOffering> findByBranchIdAndServiceId(String branchId, String serviceId) {
        return findMatching(offering -> branchId.equals(offering.getBranchId())
                        && serviceId.equals(offering.getServiceId()))
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ServiceOffering> findByIdAndBusinessId(String offeringId, String businessId) {
        return findById(offeringId).filter(offering -> branchBelongsTo(offering.getBranchId(), businessId));
    }

    @Override
    public List<ServiceOffering> findByBranchIdAndBusinessId(String branchId, String businessId) {
        if (!branchBelongsTo(branchId, businessId)) return List.of();
        return findByBranchId(branchId);
    }

    @Override
    public boolean updateForBusiness(ServiceOffering offering, String businessId) {
        return updateMatching(offering.getOfferingId(), offering,
                current -> branchBelongsTo(current.getBranchId(), businessId));
    }

    @Override
    public boolean updateForAdministrator(ServiceOffering offering) {
        return updateMatching(offering.getOfferingId(), offering, current -> true);
    }

    @Override
    public boolean existsByServiceId(String serviceId) {
        return anyMatch(offering -> serviceId.equals(offering.getServiceId()));
    }

    private boolean branchBelongsTo(String branchId, String businessId) {
        return marketplace != null && marketplace.findBranchOptional(branchId)
                .filter(branch -> businessId.equals(branch.businessId())).isPresent();
    }
}
