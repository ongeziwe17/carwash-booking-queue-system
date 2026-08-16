package com.carwash.catalog.application;

import java.math.BigDecimal;

public record CreateServiceOfferingCommand(
        String offeringId,
        String serviceId,
        BigDecimal price,
        int estimatedDurationMin,
        int concurrentCapacity
) {
    public CreateServiceOfferingCommand {
        offeringId = trim(offeringId);
        serviceId = trim(serviceId);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
