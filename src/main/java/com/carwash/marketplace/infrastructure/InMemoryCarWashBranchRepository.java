package com.carwash.marketplace.infrastructure;

import com.carwash.marketplace.domain.CarWashBranch;
import com.carwash.marketplace.domain.CarWashBranchRepository;
import com.carwash.shared.infrastructure.InMemoryRepository;

import java.util.List;

public final class InMemoryCarWashBranchRepository extends InMemoryRepository<CarWashBranch, String>
        implements CarWashBranchRepository {

    @Override
    protected String getId(CarWashBranch entity) {
        return entity.getBranchId();
    }

    @Override
    public List<CarWashBranch> findByBusinessId(String businessId) {
        return findMatching(branch -> businessId.equals(branch.getBusinessId()));
    }
}
