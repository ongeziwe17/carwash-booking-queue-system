package com.carwash.marketplace.api;

import com.carwash.marketplace.api.dto.BranchResponse;
import com.carwash.marketplace.api.dto.BusinessResponse;
import com.carwash.marketplace.application.BranchSnapshot;
import com.carwash.marketplace.application.BusinessSnapshot;

final class MarketplaceMapper {

    private MarketplaceMapper() {
    }

    static BusinessResponse toResponse(BusinessSnapshot business) {
        return new BusinessResponse(
                business.businessId(),
                business.businessName(),
                business.contactEmail(),
                business.contactPhone(),
                business.registrationNumber(),
                business.status(),
                business.registeredAt(),
                business.updatedAt()
        );
    }

    static BranchResponse toResponse(BranchSnapshot branch) {
        return new BranchResponse(
                branch.branchId(),
                branch.businessId(),
                branch.branchName(),
                branch.addressLine1(),
                branch.addressLine2(),
                branch.city(),
                branch.province(),
                branch.postalCode(),
                branch.countryCode(),
                branch.latitude(),
                branch.longitude(),
                branch.timezone(),
                branch.status(),
                branch.publicDiscoveryEnabled(),
                branch.effectiveActive(),
                branch.discoverable(),
                branch.createdAt(),
                branch.updatedAt()
        );
    }
}
