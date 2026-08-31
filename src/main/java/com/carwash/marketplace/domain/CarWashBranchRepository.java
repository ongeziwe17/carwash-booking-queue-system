package com.carwash.marketplace.domain;

import com.carwash.shared.domain.Repository;

import java.util.List;
import java.util.Optional;

public interface CarWashBranchRepository extends Repository<CarWashBranch, String> {

    List<CarWashBranch> findByBusinessId(String businessId);
    Optional<CarWashBranch> findByIdAndBusinessId(String branchId, String businessId);
}
