package com.carwash.marketplace.api;

import com.carwash.marketplace.api.dto.BranchResponse;
import com.carwash.marketplace.api.dto.BranchOpenStatusResponse;
import com.carwash.marketplace.api.dto.BranchOperatingHoursResponse;
import com.carwash.marketplace.api.dto.BusinessResponse;
import com.carwash.marketplace.api.dto.TemporaryClosureResponse;
import com.carwash.marketplace.api.dto.DiscoverableBranchResponse;
import com.carwash.marketplace.api.dto.WeeklyOperatingIntervalResponse;
import com.carwash.marketplace.application.BranchOpenStatusSnapshot;
import com.carwash.marketplace.application.BranchOperatingScheduleSnapshot;
import com.carwash.marketplace.application.BranchSnapshot;
import com.carwash.marketplace.application.BusinessSnapshot;
import com.carwash.marketplace.application.TemporaryBranchClosureSnapshot;

import java.time.ZoneOffset;

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

    static DiscoverableBranchResponse toDiscoverableResponse(BranchSnapshot branch) {
        return new DiscoverableBranchResponse(
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
                branch.timezone()
        );
    }

    static BranchOperatingHoursResponse toResponse(BranchOperatingScheduleSnapshot schedule) {
        return new BranchOperatingHoursResponse(
                schedule.branchId(),
                schedule.timezone(),
                schedule.intervals().stream()
                        .map(interval -> new WeeklyOperatingIntervalResponse(
                                interval.dayOfWeek(), interval.opensAt(), interval.closesAt()))
                        .toList(),
                schedule.createdAt(),
                schedule.updatedAt()
        );
    }

    static TemporaryClosureResponse toResponse(TemporaryBranchClosureSnapshot closure) {
        return new TemporaryClosureResponse(
                closure.closureId(),
                closure.branchId(),
                closure.startAt().atOffset(ZoneOffset.UTC),
                closure.endAt().atOffset(ZoneOffset.UTC),
                closure.reason(),
                closure.status(),
                closure.createdAt(),
                closure.updatedAt()
        );
    }

    static BranchOpenStatusResponse toResponse(BranchOpenStatusSnapshot status) {
        return new BranchOpenStatusResponse(
                status.branchId(),
                status.requestedAt().atOffset(ZoneOffset.UTC),
                status.timezone(),
                status.branchLocalDateTime(),
                status.effectiveActive(),
                status.withinWeeklyHours(),
                status.temporarilyClosed(),
                status.open(),
                status.applicableClosureId(),
                status.applicableClosureReason()
        );
    }
}
