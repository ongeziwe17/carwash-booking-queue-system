package com.carwash.marketplace.application;

import com.carwash.marketplace.domain.BranchStatus;
import com.carwash.marketplace.domain.CarWashBranch;
import com.carwash.marketplace.domain.CarWashBusiness;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BranchSnapshot(
        String branchId,
        String businessId,
        String branchName,
        String addressLine1,
        String addressLine2,
        String city,
        String province,
        String postalCode,
        String countryCode,
        BigDecimal latitude,
        BigDecimal longitude,
        String timezone,
        BranchStatus status,
        boolean publicDiscoveryEnabled,
        boolean effectiveActive,
        boolean discoverable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static BranchSnapshot from(CarWashBranch branch, CarWashBusiness business) {
        boolean effectiveActive = branch.isActive() && business.isActive();
        return new BranchSnapshot(
                branch.getBranchId(),
                branch.getBusinessId(),
                branch.getBranchName(),
                branch.getAddressLine1(),
                branch.getAddressLine2(),
                branch.getCity(),
                branch.getProvince(),
                branch.getPostalCode(),
                branch.getCountryCode(),
                branch.getLatitude(),
                branch.getLongitude(),
                branch.getTimezone(),
                branch.getStatus(),
                branch.isPublicDiscoveryEnabled(),
                effectiveActive,
                effectiveActive && branch.isPublicDiscoveryEnabled(),
                branch.getCreatedAt(),
                branch.getUpdatedAt()
        );
    }
}
