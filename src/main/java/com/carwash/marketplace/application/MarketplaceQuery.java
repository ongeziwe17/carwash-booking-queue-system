package com.carwash.marketplace.application;

import java.util.List;
import java.util.Optional;

public interface MarketplaceQuery {

    default List<BusinessSnapshot> findAllBusinesses() {
        return List.of();
    }

    default List<BranchSnapshot> findAllBranches() {
        return findDiscoverableBranches();
    }

    Optional<BusinessSnapshot> findBusinessOptional(String businessId);

    Optional<BranchSnapshot> findBranchOptional(String branchId);

    List<BranchSnapshot> findBranchesByBusiness(String businessId);

    List<BranchSnapshot> findDiscoverableBranches();
}
