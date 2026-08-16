package com.carwash.catalog.api.dto;

import com.carwash.catalog.domain.ServiceOfferingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ServiceOfferingResponse(
        String offeringId,
        String branchId,
        String serviceId,
        String serviceName,
        String serviceDescription,
        BigDecimal price,
        int estimatedDurationMin,
        int concurrentCapacity,
        ServiceOfferingStatus status,
        boolean effectiveActive,
        boolean discoverable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
