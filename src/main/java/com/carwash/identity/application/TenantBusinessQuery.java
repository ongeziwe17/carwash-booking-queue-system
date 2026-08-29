package com.carwash.identity.application;

/** Inverted, narrow validation port for canonical Marketplace tenant identities. */
@FunctionalInterface
public interface TenantBusinessQuery {

    boolean exists(String businessId);
}
