package com.carwash.catalog.api;

import com.carwash.catalog.api.dto.DiscoverableServiceOfferingResponse;
import com.carwash.catalog.api.dto.ServiceOfferingResponse;
import com.carwash.catalog.application.ServiceOfferingSnapshot;

final class ServiceOfferingMapper {

    private ServiceOfferingMapper() {
    }

    static ServiceOfferingResponse toManagementResponse(ServiceOfferingSnapshot snapshot) {
        return new ServiceOfferingResponse(
                snapshot.offeringId(),
                snapshot.branchId(),
                snapshot.serviceId(),
                snapshot.serviceName(),
                snapshot.serviceDescription(),
                snapshot.price(),
                snapshot.estimatedDurationMin(),
                snapshot.concurrentCapacity(),
                snapshot.status(),
                snapshot.effectiveActive(),
                snapshot.discoverable(),
                snapshot.createdAt(),
                snapshot.updatedAt()
        );
    }

    static DiscoverableServiceOfferingResponse toDiscoveryResponse(ServiceOfferingSnapshot snapshot) {
        return new DiscoverableServiceOfferingResponse(
                snapshot.offeringId(),
                snapshot.branchId(),
                snapshot.serviceId(),
                snapshot.serviceName(),
                snapshot.serviceDescription(),
                snapshot.price(),
                snapshot.estimatedDurationMin(),
                snapshot.concurrentCapacity()
        );
    }
}
