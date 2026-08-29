package com.carwash.identity.domain;

import com.carwash.shared.domain.Repository;

import java.util.List;

public interface TenantMembershipRepository extends Repository<TenantMembership, String> {

    List<TenantMembership> findByBusinessId(String businessId);
}
