package com.carwash.marketplace.infrastructure;

import com.carwash.marketplace.domain.CarWashBranch;
import com.carwash.marketplace.domain.CarWashBranchRepository;
import com.carwash.shared.infrastructure.InMemoryRepository;

import java.util.List;
import java.util.Optional;

public final class InMemoryCarWashBranchRepository extends InMemoryRepository<CarWashBranch, String>
        implements CarWashBranchRepository {
    @Override public com.carwash.shared.application.PageResult<CarWashBranch> findPage(com.carwash.shared.application.ListQuery query) {
        var scope = query.scope();
        java.util.List<CarWashBranch> source = scope.businessId() == null ? findMatching(v -> true) : findByBusinessId(scope.businessId());
        if (scope.branchId() != null) source = source.stream().filter(v -> scope.branchId().equals(v.getBranchId())).toList();
        if (scope.discoverable()) source = source.stream().filter(v -> v.isActive() && v.isPublicDiscoveryEnabled() && businesses != null && businesses.findById(v.getBusinessId()).map(com.carwash.marketplace.domain.CarWashBusiness::isActive).orElse(false)).toList();
        return query.select(source, java.util.Map.ofEntries(java.util.Map.entry("id", (java.util.function.Function<CarWashBranch, Object>) v -> v.getBranchId()),
                java.util.Map.entry("name", (java.util.function.Function<CarWashBranch, Object>) v -> v.getBranchName()),
                java.util.Map.entry("createdAt", (java.util.function.Function<CarWashBranch, Object>) v -> v.getCreatedAt()),
                java.util.Map.entry("status", (java.util.function.Function<CarWashBranch, Object>) v -> v.getStatus()),
                java.util.Map.entry("city", (java.util.function.Function<CarWashBranch, Object>) v -> v.getCity())));
    }


    private final com.carwash.marketplace.domain.CarWashBusinessRepository businesses;
    public InMemoryCarWashBranchRepository() { this(null); }
    public InMemoryCarWashBranchRepository(com.carwash.marketplace.domain.CarWashBusinessRepository businesses) { this.businesses = businesses; }

    @Override
    protected String getId(CarWashBranch entity) {
        return entity.getBranchId();
    }

    @Override
    public List<CarWashBranch> findByBusinessId(String businessId) {
        return findMatching(branch -> businessId.equals(branch.getBusinessId()));
    }

    @Override
    public Optional<CarWashBranch> findByIdAndBusinessId(String branchId, String businessId) {
        return findMatching(branch -> branchId != null && branchId.equals(branch.getBranchId())
                && businessId != null && businessId.equals(branch.getBusinessId())).stream().findFirst();
    }

    @Override
    public boolean updateForBusiness(CarWashBranch branch, String businessId) {
        return updateMatching(branch.getBranchId(), branch,
                current -> businessId != null && businessId.equals(current.getBusinessId()));
    }

    @Override
    public boolean updateForAdministrator(CarWashBranch branch) {
        return updateMatching(branch.getBranchId(), branch, current -> true);
    }
}
