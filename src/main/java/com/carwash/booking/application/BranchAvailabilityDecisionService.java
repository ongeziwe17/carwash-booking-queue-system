package com.carwash.booking.application;

import com.carwash.booking.domain.Booking;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.catalog.application.ServiceDefinitionQuery;
import com.carwash.catalog.application.ServiceDefinitionSnapshot;
import com.carwash.catalog.application.ServiceOfferingQuery;
import com.carwash.catalog.application.ServiceOfferingSnapshot;
import com.carwash.marketplace.application.BranchScheduleQuery;
import com.carwash.marketplace.application.BranchServiceWindowSnapshot;
import com.carwash.marketplace.application.BranchSnapshot;
import com.carwash.marketplace.application.MarketplaceQuery;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * Canonical branch/offering/window/capacity policy. It deliberately excludes customer and vehicle conflicts,
 * which remain booking-command concerns.
 */
public final class BranchAvailabilityDecisionService implements BranchAvailabilityQuery {

    private final BookingRepository bookingRepository;
    private final MarketplaceQuery marketplaceQuery;
    private final BranchScheduleQuery branchScheduleQuery;
    private final ServiceOfferingQuery serviceOfferingQuery;
    private final ServiceDefinitionQuery serviceDefinitionQuery;
    private final BookingPolicyProperties bookingPolicy;
    private final Clock clock;

    public BranchAvailabilityDecisionService(
            BookingRepository bookingRepository,
            MarketplaceQuery marketplaceQuery,
            BranchScheduleQuery branchScheduleQuery,
            ServiceOfferingQuery serviceOfferingQuery,
            ServiceDefinitionQuery serviceDefinitionQuery,
            BookingPolicyProperties bookingPolicy,
            Clock clock
    ) {
        this.bookingRepository = Objects.requireNonNull(bookingRepository, "Booking repository is required");
        this.marketplaceQuery = Objects.requireNonNull(marketplaceQuery, "Marketplace query is required");
        this.branchScheduleQuery = Objects.requireNonNull(branchScheduleQuery, "Branch schedule query is required");
        this.serviceOfferingQuery = Objects.requireNonNull(serviceOfferingQuery, "Service offering query is required");
        this.serviceDefinitionQuery = Objects.requireNonNull(serviceDefinitionQuery,
                "Service definition query is required");
        this.bookingPolicy = Objects.requireNonNull(bookingPolicy, "Booking policy is required");
        this.clock = Objects.requireNonNull(clock, "Application clock is required");
    }

    @Override
    public BranchAvailabilityDecisionSnapshot evaluate(
            String branchId,
            String serviceOfferingId,
            Instant startsAt,
            String excludedBookingId
    ) {
        String normalizedBranchId = normalizeId(branchId, "Branch ID");
        String normalizedOfferingId = normalizeId(serviceOfferingId, "Service offering ID");
        if (startsAt == null) {
            throw new BusinessRuleViolationException("Availability start instant is required");
        }
        BranchSnapshot branch = marketplaceQuery.findBranchOptional(normalizedBranchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + normalizedBranchId));
        ServiceOfferingSnapshot offering = serviceOfferingQuery.findOfferingOptional(normalizedOfferingId)
                .orElseThrow(() -> new ResourceNotFoundException("Offering not found: " + normalizedOfferingId));
        if (!branch.branchId().equals(offering.branchId())) {
            throw new BusinessRuleViolationException("Service offering does not belong to the requested branch");
        }
        ServiceDefinitionSnapshot service = serviceDefinitionQuery
                .findServiceDefinitionOptional(offering.serviceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + offering.serviceId()));

        Instant endsAt = startsAt.plus(Duration.ofMinutes(offering.estimatedDurationMin()));
        ZoneId zone = ZoneId.of(branch.timezone());
        BranchServiceWindowSnapshot window = branchScheduleQuery
                .getServiceWindowStatus(branch.branchId(), startsAt, endsAt);
        boolean future = startsAt.isAfter(clock.instant());
        boolean aligned = isAligned(startsAt, zone);
        boolean effectiveEligible = branch.effectiveActive() && offering.effectiveActive() && service.active();
        int occupied = occupiedCapacity(
                branch.branchId(), offering.offeringId(), startsAt, endsAt, zone, excludedBookingId);
        int remaining = Math.max(0, offering.concurrentCapacity() - occupied);

        BranchAvailabilityReason reason = reason(
                future, aligned, branch, offering, service, window, remaining);
        boolean available = reason == BranchAvailabilityReason.AVAILABLE;
        return new BranchAvailabilityDecisionSnapshot(
                branch.branchId(),
                offering.offeringId(),
                service.serviceId(),
                service.serviceName(),
                offering.price(),
                offering.estimatedDurationMin(),
                zone.getId(),
                startsAt,
                endsAt,
                startsAt.atZone(zone).toOffsetDateTime(),
                endsAt.atZone(zone).toOffsetDateTime(),
                effectiveEligible,
                window.withinWeeklyHours(),
                window.temporarilyClosed(),
                offering.concurrentCapacity(),
                occupied,
                remaining,
                available,
                reason
        );
    }

    public BranchAvailabilityDecisionSnapshot evaluateLocal(
            String branchId,
            String serviceOfferingId,
            LocalDateTime branchLocalStartsAt,
            String excludedBookingId
    ) {
        BranchSnapshot branch = requireBranch(branchId);
        Instant startsAt = resolveUnambiguousInstant(branchLocalStartsAt, ZoneId.of(branch.timezone()));
        return evaluate(branch.branchId(), serviceOfferingId, startsAt, excludedBookingId);
    }

    public BranchAvailabilityDecisionSnapshot requireAvailableLocal(
            String branchId,
            String serviceOfferingId,
            LocalDateTime branchLocalStartsAt,
            String excludedBookingId
    ) {
        BranchAvailabilityDecisionSnapshot decision = evaluateLocal(
                branchId, serviceOfferingId, branchLocalStartsAt, excludedBookingId);
        if (!decision.available()) {
            throw new BusinessRuleViolationException(message(decision.reason()));
        }
        return decision;
    }

    private int occupiedCapacity(
            String branchId,
            String offeringId,
            Instant startsAt,
            Instant endsAt,
            ZoneId zone,
            String excludedBookingId
    ) {
        return (int) bookingRepository.findByBranchId(branchId).stream()
                .filter(booking -> offeringId.equals(booking.getServiceOfferingId()))
                .filter(booking -> !Objects.equals(excludedBookingId, booking.getBookingId()))
                .filter(this::occupiesCapacity)
                .filter(booking -> overlaps(booking, startsAt, endsAt, zone))
                .count();
    }

    private boolean overlaps(Booking booking, Instant startsAt, Instant endsAt, ZoneId zone) {
        if (booking.getScheduledDateTime() == null) {
            return false;
        }
        Instant existingStart = booking.getScheduledDateTime().atZone(zone).toInstant();
        ServiceOfferingSnapshot existingOffering = serviceOfferingQuery
                .findOfferingOptional(booking.getServiceOfferingId())
                .orElse(null);
        if (existingOffering == null || !existingOffering.branchId().equals(booking.getBranchId())) {
            return false;
        }
        Instant existingEnd = existingStart.plus(Duration.ofMinutes(existingOffering.estimatedDurationMin()));
        return existingStart.isBefore(endsAt) && startsAt.isBefore(existingEnd);
    }

    private boolean occupiesCapacity(Booking booking) {
        return booking.getStatus() != BookingStatus.CANCELLED
                && booking.getStatus() != BookingStatus.COMPLETED;
    }

    private boolean isAligned(Instant startsAt, ZoneId zone) {
        LocalDateTime local = LocalDateTime.ofInstant(startsAt, zone);
        long intervalSeconds = bookingPolicy.slotInterval().getSeconds();
        return local.getNano() == 0 && local.toLocalTime().toSecondOfDay() % intervalSeconds == 0;
    }

    private BranchAvailabilityReason reason(
            boolean future,
            boolean aligned,
            BranchSnapshot branch,
            ServiceOfferingSnapshot offering,
            ServiceDefinitionSnapshot service,
            BranchServiceWindowSnapshot window,
            int remaining
    ) {
        if (!future) return BranchAvailabilityReason.NOT_IN_FUTURE;
        if (!aligned) return BranchAvailabilityReason.MISALIGNED_SLOT;
        if (!branch.effectiveActive()) return BranchAvailabilityReason.INACTIVE_BRANCH;
        if (!offering.effectiveActive()) return BranchAvailabilityReason.INACTIVE_OFFERING;
        if (!service.active()) return BranchAvailabilityReason.INACTIVE_SERVICE;
        if (!window.withinWeeklyHours()) return BranchAvailabilityReason.OUTSIDE_OPERATING_HOURS;
        if (window.temporarilyClosed()) return BranchAvailabilityReason.TEMPORARILY_CLOSED;
        if (remaining == 0) return BranchAvailabilityReason.CAPACITY_FULL;
        return BranchAvailabilityReason.AVAILABLE;
    }

    private String message(BranchAvailabilityReason reason) {
        return switch (reason) {
            case NOT_IN_FUTURE -> "Scheduled date/time must be in the future";
            case INVALID_LOCAL_TIME -> "Scheduled date/time is invalid in the branch timezone";
            case MISALIGNED_SLOT -> "Scheduled date/time must align with the configured slot interval";
            case INACTIVE_BRANCH -> "Inactive branch or owning business cannot accept operational bookings";
            case INACTIVE_OFFERING -> "Inactive service offering or reusable service cannot be booked";
            case INACTIVE_SERVICE -> "Inactive reusable service cannot be booked";
            case OUTSIDE_OPERATING_HOURS -> "Selected service does not fit within branch operating hours";
            case TEMPORARILY_CLOSED -> "Selected service window overlaps a temporary branch closure";
            case CAPACITY_FULL -> "Booking time slot is already full";
            case AVAILABLE -> "Service offering is available";
        };
    }

    private BranchSnapshot requireBranch(String branchId) {
        String normalized = normalizeId(branchId, "Branch ID");
        return marketplaceQuery.findBranchOptional(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + normalized));
    }

    private Instant resolveUnambiguousInstant(LocalDateTime localDateTime, ZoneId zone) {
        if (localDateTime == null) {
            throw new BusinessRuleViolationException("Scheduled date/time is required");
        }
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(localDateTime);
        if (offsets.size() != 1) {
            throw new BusinessRuleViolationException(
                    offsets.isEmpty()
                            ? "Scheduled date/time does not exist in the branch timezone"
                            : "Scheduled date/time is ambiguous in the branch timezone");
        }
        return localDateTime.toInstant(offsets.getFirst());
    }

    private String normalizeId(String value, String field) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new BusinessRuleViolationException(field + " is required");
        }
        if (normalized.length() > 64) {
            throw new BusinessRuleViolationException(field + " must not exceed 64 characters");
        }
        return normalized;
    }
}
