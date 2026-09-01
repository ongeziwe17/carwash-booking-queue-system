package com.carwash.marketplace.domain;

import com.carwash.shared.domain.Repository;

import java.util.Optional;

public interface BranchOperatingScheduleRepository extends Repository<BranchOperatingSchedule, String> {

    Optional<BranchOperatingSchedule> findByBranchIdAndBusinessId(String branchId, String businessId);
    boolean updateForBusiness(BranchOperatingSchedule schedule, String businessId);
    boolean updateForAdministrator(BranchOperatingSchedule schedule);
}
