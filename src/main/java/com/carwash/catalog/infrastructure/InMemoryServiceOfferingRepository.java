package com.carwash.catalog.infrastructure;

import com.carwash.catalog.domain.ServiceOffering;
import com.carwash.catalog.domain.ServiceOfferingRepository;
import com.carwash.shared.infrastructure.InMemoryRepository;

import java.util.List;
import java.util.Optional;

public final class InMemoryServiceOfferingRepository
        extends InMemoryRepository<ServiceOffering, String>
        implements ServiceOfferingRepository {

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
    public boolean existsByServiceId(String serviceId) {
        return anyMatch(offering -> serviceId.equals(offering.getServiceId()));
    }
}
