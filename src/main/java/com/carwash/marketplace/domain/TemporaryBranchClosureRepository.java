package com.carwash.marketplace.domain;

import com.carwash.shared.domain.Repository;

import java.util.List;

public interface TemporaryBranchClosureRepository extends Repository<TemporaryBranchClosure, String> {

    List<TemporaryBranchClosure> findByBranchId(String branchId);
}
