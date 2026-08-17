package com.carwash.reporting.application;

import com.carwash.booking.application.BookingQuery;
import com.carwash.booking.application.BookingSnapshot;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.marketplace.application.BranchSnapshot;
import com.carwash.marketplace.application.BusinessSnapshot;
import com.carwash.marketplace.application.MarketplaceQuery;
import com.carwash.queue.application.QueueEntrySnapshot;
import com.carwash.queue.application.QueueQuery;
import com.carwash.queue.domain.QueueStatus;
import com.carwash.reporting.api.dto.DailySummaryReportResponse;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class DailySummaryReportService {

    private static final String MULTIPLE_TIMEZONES = "MULTIPLE_BRANCH_TIMEZONES";
    private static final String NO_BRANCH_TIMEZONE = "NO_BRANCH_TIMEZONE";

    private final BookingQuery bookingQuery;
    private final QueueQuery queueQuery;
    private final MarketplaceQuery marketplaceQuery;
    private final InMemoryDataCoordinator coordinator;

    public DailySummaryReportService(
            BookingQuery bookingQuery,
            QueueQuery queueQuery,
            MarketplaceQuery marketplaceQuery,
            InMemoryDataCoordinator coordinator
    ) {
        this.bookingQuery = Objects.requireNonNull(bookingQuery, "Booking query is required");
        this.queueQuery = Objects.requireNonNull(queueQuery, "Queue query is required");
        this.marketplaceQuery = Objects.requireNonNull(marketplaceQuery, "Marketplace query is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
    }

    public DailySummaryReportResponse generateDailySummary(
            LocalDate reportDate,
            String branchId,
            String businessId
    ) {
        if (reportDate == null) {
            throw new BusinessRuleViolationException("Report date is required");
        }
        boolean hasBranch = branchId != null;
        boolean hasBusiness = businessId != null;
        if (hasBranch == hasBusiness) {
            throw new BusinessRuleViolationException("Exactly one of branchId or businessId is required");
        }
        return coordinator.read(() -> hasBranch
                ? branchSummary(reportDate, branchId)
                : businessSummary(reportDate, businessId));
    }

    private DailySummaryReportResponse branchSummary(LocalDate reportDate, String branchId) {
        BranchSnapshot branch = requireBranch(branchId);
        ZoneId.of(branch.timezone());
        return summarize(
                reportDate,
                "BRANCH",
                branch.branchId(),
                branch.timezone(),
                bookingQuery.findBookingSnapshotsByBranch(branch.branchId()),
                queueQuery.findQueueEntrySnapshotsByBranch(branch.branchId())
        );
    }

    private DailySummaryReportResponse businessSummary(LocalDate reportDate, String businessId) {
        String normalizedBusinessId = normalizeId(businessId, "Business ID");
        BusinessSnapshot business = marketplaceQuery.findBusinessOptional(normalizedBusinessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + normalizedBusinessId));
        List<BranchSnapshot> branches = marketplaceQuery.findBranchesByBusiness(business.businessId());
        Set<String> branchIds = branches.stream().map(BranchSnapshot::branchId).collect(Collectors.toUnmodifiableSet());
        long timezoneCount = branches.stream().map(BranchSnapshot::timezone).distinct().count();
        String timezone = timezoneCount == 0
                ? NO_BRANCH_TIMEZONE
                : timezoneCount == 1 ? branches.getFirst().timezone() : MULTIPLE_TIMEZONES;
        List<BookingSnapshot> bookings = bookingQuery.findBookingSnapshots().stream()
                .filter(booking -> branchIds.contains(booking.branchId()))
                .toList();
        List<QueueEntrySnapshot> queueEntries = queueQuery.findQueueEntrySnapshots().stream()
                .filter(entry -> branchIds.contains(entry.branchId()))
                .toList();
        return summarize(reportDate, "BUSINESS", business.businessId(), timezone, bookings, queueEntries);
    }

    private DailySummaryReportResponse summarize(
            LocalDate reportDate,
            String scopeType,
            String scopeId,
            String timezone,
            List<BookingSnapshot> scopedBookings,
            List<QueueEntrySnapshot> scopedQueueEntries
    ) {
        List<BookingSnapshot> bookingsForDate = scopedBookings.stream()
                .filter(booking -> booking.scheduledDateTime() != null)
                .filter(booking -> booking.scheduledDateTime().toLocalDate().equals(reportDate))
                .toList();
        List<QueueEntrySnapshot> queueEntriesForDate = scopedQueueEntries.stream()
                .filter(queueEntry -> queueEntry.scheduledDateTime() != null)
                .filter(queueEntry -> queueEntry.scheduledDateTime().toLocalDate().equals(reportDate))
                .toList();
        long confirmedBookings = countBookingsByStatus(bookingsForDate, BookingStatus.CONFIRMED);
        long cancelledBookings = countBookingsByStatus(bookingsForDate, BookingStatus.CANCELLED);
        long completedBookings = countBookingsByStatus(bookingsForDate, BookingStatus.COMPLETED);
        long pendingWorkload = bookingsForDate.stream()
                .filter(booking -> booking.status() != BookingStatus.CANCELLED)
                .filter(booking -> booking.status() != BookingStatus.COMPLETED)
                .count();
        return new DailySummaryReportResponse(
                scopeType,
                scopeId,
                timezone,
                reportDate,
                bookingsForDate.size(),
                confirmedBookings,
                cancelledBookings,
                completedBookings,
                queueEntriesForDate.size(),
                countQueueEntriesByStatus(queueEntriesForDate, QueueStatus.WAITING),
                countQueueEntriesByStatus(queueEntriesForDate, QueueStatus.CALLED),
                countQueueEntriesByStatus(queueEntriesForDate, QueueStatus.IN_PROGRESS),
                countQueueEntriesByStatus(queueEntriesForDate, QueueStatus.COMPLETED),
                pendingWorkload
        );
    }

    private BranchSnapshot requireBranch(String branchId) {
        String normalizedBranchId = normalizeId(branchId, "Branch ID");
        return marketplaceQuery.findBranchOptional(normalizedBranchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + normalizedBranchId));
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

    private long countBookingsByStatus(List<BookingSnapshot> bookings, BookingStatus status) {
        return bookings.stream().filter(booking -> booking.status() == status).count();
    }

    private long countQueueEntriesByStatus(List<QueueEntrySnapshot> queueEntries, QueueStatus status) {
        return queueEntries.stream().filter(queueEntry -> queueEntry.status() == status).count();
    }
}
