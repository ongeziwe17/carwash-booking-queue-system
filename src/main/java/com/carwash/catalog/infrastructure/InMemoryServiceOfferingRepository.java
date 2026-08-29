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

    private final MarketplaceQuery marketplace;

    public InMemoryServiceOfferingRepository() {
        this(null);
    }

    public InMemoryServiceOfferingRepository(MarketplaceQuery marketplace) {
        this.marketplace = marketplace;
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
    public boolean existsByServiceId(String serviceId) {
        return anyMatch(offering -> serviceId.equals(offering.getServiceId()));
    }

    private boolean branchBelongsTo(String branchId, String businessId) {
        return marketplace != null && marketplace.findBranchOptional(branchId)
                .filter(branch -> businessId.equals(branch.businessId())).isPresent();
    }
}
