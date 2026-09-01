package com.carwash.marketplace.infrastructure;

import com.carwash.marketplace.domain.BranchOperatingSchedule;
import com.carwash.marketplace.domain.BranchOperatingScheduleRepository;
import com.carwash.marketplace.domain.CarWashBranchRepository;
import com.carwash.shared.infrastructure.InMemoryRepository;

import java.util.Optional;

public final class InMemoryBranchOperatingScheduleRepository
        extends InMemoryRepository<BranchOperatingSchedule, String>
        implements BranchOperatingScheduleRepository {

    private final CarWashBranchRepository branches;

    public InMemoryBranchOperatingScheduleRepository() {
        this(null);
    }

    public InMemoryBranchOperatingScheduleRepository(CarWashBranchRepository branches) {
        this.branches = branches;
    }

    @Override
    protected String getId(BranchOperatingSchedule entity) {
        return entity.getBranchId();
    }

    @Override
    public Optional<BranchOperatingSchedule> findByBranchIdAndBusinessId(String branchId, String businessId) {
        if (branches == null || branches.findByIdAndBusinessId(branchId, businessId).isEmpty()) {
            return Optional.empty();
        }
        return findById(branchId);
    }

    @Override
    public boolean updateForBusiness(BranchOperatingSchedule schedule, String businessId) {
        return updateMatching(schedule.getBranchId(), schedule,
                current -> branches != null
                        && branches.findByIdAndBusinessId(current.getBranchId(), businessId).isPresent());
    }

    @Override
    public boolean updateForAdministrator(BranchOperatingSchedule schedule) {
        return updateMatching(schedule.getBranchId(), schedule, current -> true);
    }
}
