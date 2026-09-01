package com.carwash.marketplace.infrastructure;

import com.carwash.marketplace.domain.CarWashBranch;
import com.carwash.marketplace.domain.CarWashBranchRepository;
import com.carwash.shared.infrastructure.InMemoryRepository;

import java.util.List;
import java.util.Optional;

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

    @Override
    public Optional<CarWashBranch> findByIdAndBusinessId(String branchId, String businessId) {
        return findMatching(branch -> branchId != null && branchId.equals(branch.getBranchId())
                && businessId != null && businessId.equals(branch.getBusinessId())).stream().findFirst();
    }

    @Override
    public boolean updateForBusiness(CarWashBranch branch, String businessId) {
        return updateMatching(branch.getBranchId(), branch,
                current -> businessId != null && businessId.equals(current.getBusinessId()));
    }

    @Override
    public boolean updateForAdministrator(CarWashBranch branch) {
        return updateMatching(branch.getBranchId(), branch, current -> true);
    }
}
