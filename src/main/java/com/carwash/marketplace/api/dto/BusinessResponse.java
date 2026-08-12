package com.carwash.marketplace.api.dto;

import com.carwash.marketplace.domain.BusinessStatus;

import java.time.LocalDateTime;

public record BusinessResponse(
        String businessId,
        String businessName,
        String contactEmail,
        String contactPhone,
        String registrationNumber,
        BusinessStatus status,
        LocalDateTime registeredAt,
        LocalDateTime updatedAt
) {
}
