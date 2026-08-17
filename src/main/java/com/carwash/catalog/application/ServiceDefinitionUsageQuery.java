package com.carwash.catalog.application;

/**
 * Catalog-owned reference check implemented by composition code so Catalog does not reach into
 * Booking or Queue repositories.
 */
public interface ServiceDefinitionUsageQuery {

    boolean referencedByBooking(String serviceId);

    boolean referencedByQueue(String serviceId);
}
