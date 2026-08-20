package com.carwash.catalog.application;

/**
 * Catalogue-owned port for checking whether proposed offering terms can contain
 * the active bookings that already reference the offering.
 */
@FunctionalInterface
public interface ServiceOfferingCapacityQuery {

    int maximumConcurrentActiveBookings(String offeringId, int estimatedDurationMin);

    static ServiceOfferingCapacityQuery empty() {
        return (offeringId, estimatedDurationMin) -> 0;
    }
}
