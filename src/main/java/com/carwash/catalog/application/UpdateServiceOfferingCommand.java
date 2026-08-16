package com.carwash.catalog.application;

import java.math.BigDecimal;

public record UpdateServiceOfferingCommand(
        BigDecimal price,
        int estimatedDurationMin,
        int concurrentCapacity
) {
}
