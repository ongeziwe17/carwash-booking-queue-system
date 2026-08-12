package com.carwash.marketplace.infrastructure;

import com.carwash.marketplace.domain.TemporaryBranchClosure;
import com.carwash.marketplace.domain.TemporaryBranchClosureRepository;
import com.carwash.shared.infrastructure.InMemoryRepository;

import java.util.List;

public final class InMemoryTemporaryBranchClosureRepository
        extends InMemoryRepository<TemporaryBranchClosure, String>
        implements TemporaryBranchClosureRepository {

    @Override
    protected String getId(TemporaryBranchClosure entity) {
        return entity.getClosureId();
    }

    @Override
    public List<TemporaryBranchClosure> findByBranchId(String branchId) {
        return findMatching(closure -> branchId.equals(closure.getBranchId()));
    }
}
