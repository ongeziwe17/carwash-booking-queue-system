package com.carwash.identity.application;

import com.carwash.identity.domain.TenantMembership;

import java.util.List;
import java.util.Optional;

/** Published identity contract used by authentication and tenant-aware administration. */
public interface TenantMembershipQuery {

    Optional<TenantMembership> findByUserId(String userId);

    List<TenantMembership> findByBusinessId(String businessId);
}
