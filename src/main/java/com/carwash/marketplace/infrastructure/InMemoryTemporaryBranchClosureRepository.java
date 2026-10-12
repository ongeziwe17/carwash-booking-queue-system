package com.carwash.marketplace.infrastructure;

import com.carwash.marketplace.domain.TemporaryBranchClosure;
import com.carwash.marketplace.domain.TemporaryBranchClosureRepository;
import com.carwash.marketplace.domain.CarWashBranchRepository;
import com.carwash.shared.infrastructure.InMemoryRepository;

import java.util.List;
import java.util.Optional;

public final class InMemoryTemporaryBranchClosureRepository
        extends InMemoryRepository<TemporaryBranchClosure, String>
        implements TemporaryBranchClosureRepository {
    @Override public com.carwash.shared.application.PageResult<TemporaryBranchClosure> findPage(com.carwash.shared.application.ListQuery query) {
        var scope = query.scope();
        java.util.List<TemporaryBranchClosure> source = scope.branchId() == null ? findMatching(v -> true) : findByBranchId(scope.branchId());
        source = source.stream().filter(v -> scope.branchId() == null || scope.branchId().equals(v.getBranchId())).toList();
        if (scope.businessId() != null) source = source.stream().filter(v -> branchBelongsTo(v.getBranchId(), scope.businessId())).toList();
        return query.select(source, java.util.Map.ofEntries(java.util.Map.entry("id", (java.util.function.Function<TemporaryBranchClosure, Object>) v -> v.getClosureId()),
                java.util.Map.entry("startsAt", (java.util.function.Function<TemporaryBranchClosure, Object>) v -> v.getStartAt()),
                java.util.Map.entry("endsAt", (java.util.function.Function<TemporaryBranchClosure, Object>) v -> v.getEndAt()),
                java.util.Map.entry("status", (java.util.function.Function<TemporaryBranchClosure, Object>) v -> v.getStatus()),
                java.util.Map.entry("from", (java.util.function.Function<TemporaryBranchClosure, Object>) v -> v.getStartAt()),
                java.util.Map.entry("to", (java.util.function.Function<TemporaryBranchClosure, Object>) v -> v.getStartAt())));
    }


    private final CarWashBranchRepository branches;

    public InMemoryTemporaryBranchClosureRepository() {
        this(null);
    }

    public InMemoryTemporaryBranchClosureRepository(CarWashBranchRepository branches) {
        this.branches = branches;
    }

    @Override
    protected String getId(TemporaryBranchClosure entity) {
        return entity.getClosureId();
    }

    @Override
    public List<TemporaryBranchClosure> findByBranchId(String branchId) {
        return findMatching(closure -> branchId.equals(closure.getBranchId()));
    }

    @Override
    public List<TemporaryBranchClosure> findByBranchIdAndBusinessId(String branchId, String businessId) {
        if (!branchBelongsTo(branchId, businessId)) return List.of();
        return findByBranchId(branchId);
    }

    @Override
    public Optional<TemporaryBranchClosure> findByIdAndBusinessId(String closureId, String businessId) {
        return findById(closureId).filter(closure -> branchBelongsTo(closure.getBranchId(), businessId));
    }

    @Override
    public boolean updateForBusiness(TemporaryBranchClosure closure, String businessId) {
        return updateMatching(closure.getClosureId(), closure,
                current -> branchBelongsTo(current.getBranchId(), businessId));
    }

    @Override
    public boolean updateForAdministrator(TemporaryBranchClosure closure) {
        return updateMatching(closure.getClosureId(), closure, current -> true);
    }

    private boolean branchBelongsTo(String branchId, String businessId) {
        return branches != null && branches.findByIdAndBusinessId(branchId, businessId).isPresent();
    }
}
