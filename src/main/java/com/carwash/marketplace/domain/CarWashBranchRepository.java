package com.carwash.marketplace.domain;

import com.carwash.shared.domain.Repository;

import java.util.List;
import java.util.Optional;

public interface CarWashBranchRepository extends Repository<CarWashBranch, String>, com.carwash.shared.application.PagedRepository<CarWashBranch> {

    List<CarWashBranch> findByBusinessId(String businessId);
    Optional<CarWashBranch> findByIdAndBusinessId(String branchId, String businessId);
    boolean updateForBusiness(CarWashBranch branch, String businessId);
    boolean updateForAdministrator(CarWashBranch branch);
}
