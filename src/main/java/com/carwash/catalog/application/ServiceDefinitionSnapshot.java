package com.carwash.catalog.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Detached reusable-service definition published to operational consumers. */
public record ServiceDefinitionSnapshot(
        String serviceId,
        String serviceName,
        String description,
        BigDecimal legacyDefaultPrice,
        int legacyDefaultEstimatedDurationMin,
        boolean active,
        LocalDateTime createdAt
) {
}
