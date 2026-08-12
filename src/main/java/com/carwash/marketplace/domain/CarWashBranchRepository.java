package com.carwash.marketplace.domain;

import com.carwash.shared.domain.Repository;

import java.util.List;

public interface CarWashBranchRepository extends Repository<CarWashBranch, String> {

    List<CarWashBranch> findByBusinessId(String businessId);
}
