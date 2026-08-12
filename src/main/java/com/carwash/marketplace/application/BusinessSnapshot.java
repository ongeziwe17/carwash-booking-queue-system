package com.carwash.marketplace.application;

import com.carwash.marketplace.domain.BusinessStatus;
import com.carwash.marketplace.domain.CarWashBusiness;

import java.time.LocalDateTime;

public record BusinessSnapshot(
        String businessId,
        String businessName,
        String contactEmail,
        String contactPhone,
        String registrationNumber,
        BusinessStatus status,
        LocalDateTime registeredAt,
        LocalDateTime updatedAt
) {
    static BusinessSnapshot from(CarWashBusiness business) {
        return new BusinessSnapshot(
                business.getBusinessId(),
                business.getBusinessName(),
                business.getContactEmail(),
                business.getContactPhone(),
                business.getRegistrationNumber(),
                business.getStatus(),
                business.getRegisteredAt(),
                business.getUpdatedAt()
        );
    }
}
