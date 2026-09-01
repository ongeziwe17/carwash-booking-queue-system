package com.carwash.marketplace.domain;

import com.carwash.shared.domain.Repository;

import java.util.List;
import java.util.Optional;

public interface TemporaryBranchClosureRepository extends Repository<TemporaryBranchClosure, String> {

    List<TemporaryBranchClosure> findByBranchId(String branchId);
    List<TemporaryBranchClosure> findByBranchIdAndBusinessId(String branchId, String businessId);
    Optional<TemporaryBranchClosure> findByIdAndBusinessId(String closureId, String businessId);
    boolean updateForBusiness(TemporaryBranchClosure closure, String businessId);
    boolean updateForAdministrator(TemporaryBranchClosure closure);
}
