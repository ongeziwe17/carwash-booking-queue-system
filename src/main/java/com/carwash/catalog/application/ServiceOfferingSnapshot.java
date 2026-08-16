package com.carwash.catalog.application;

import com.carwash.catalog.domain.ServiceOfferingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ServiceOfferingSnapshot(
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
