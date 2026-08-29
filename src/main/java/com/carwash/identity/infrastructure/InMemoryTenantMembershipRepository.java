package com.carwash.identity.infrastructure;

import com.carwash.identity.domain.TenantMembership;
import com.carwash.identity.domain.TenantMembershipRepository;
import com.carwash.shared.infrastructure.InMemoryRepository;

import java.util.List;

public final class InMemoryTenantMembershipRepository
        extends InMemoryRepository<TenantMembership, String>
        implements TenantMembershipRepository {

    @Override
    protected String getId(TenantMembership entity) {
        return entity.userId();
    }

    @Override
    public List<TenantMembership> findByBusinessId(String businessId) {
        return findMatching(membership -> businessId.equals(membership.businessId()));
    }
}
