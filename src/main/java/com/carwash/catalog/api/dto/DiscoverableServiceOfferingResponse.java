package com.carwash.catalog.api.dto;

import java.math.BigDecimal;

public record DiscoverableServiceOfferingResponse(
        String offeringId,
        String branchId,
        String serviceId,
        String serviceName,
        String serviceDescription,
        BigDecimal price,
        int estimatedDurationMin,
        int concurrentCapacity
) {
}
