package com.carwash.marketplace.infrastructure;

import com.carwash.marketplace.domain.BranchOperatingSchedule;
import com.carwash.marketplace.domain.BranchOperatingScheduleRepository;
import com.carwash.shared.infrastructure.InMemoryRepository;

public final class InMemoryBranchOperatingScheduleRepository
        extends InMemoryRepository<BranchOperatingSchedule, String>
        implements BranchOperatingScheduleRepository {

    @Override
    protected String getId(BranchOperatingSchedule entity) {
        return entity.getBranchId();
    }
}
