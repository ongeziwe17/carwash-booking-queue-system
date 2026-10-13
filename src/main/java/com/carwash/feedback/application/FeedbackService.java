package com.carwash.feedback.application;

import com.carwash.access.application.TenantAccessContext;
import com.carwash.audit.application.*;
import com.carwash.audit.domain.*;
import com.carwash.booking.application.*;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.catalog.application.ServiceOfferingQuery;
import com.carwash.feedback.domain.*;
import com.carwash.marketplace.application.*;
import com.carwash.shared.application.*;
import com.carwash.shared.exception.*;
import java.time.*;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

/** Feedback policy. Every scope and eligibility decision is based on server-owned projections. */
public final class FeedbackService {
    private final FeedbackRepository repository;
    private final BookingQuery bookings;
    private final MarketplaceQuery marketplace;
    private final ServiceOfferingQuery offerings;
    private final DataTransactionOperations transactions;
    private final MutationLock locks;
    private final AuditOperations audit;
    private final Telemetry telemetry;
    private final Clock clock;

    public FeedbackService(FeedbackRepository repository, BookingQuery bookings, MarketplaceQuery marketplace,
            ServiceOfferingQuery offerings, DataTransactionOperations transactions, MutationLock locks,
            AuditOperations audit, Telemetry telemetry, Clock clock) {
        this.repository = repository; this.bookings = bookings; this.marketplace = marketplace;
        this.offerings = offerings; this.transactions = transactions; this.locks = locks;
        this.audit = audit; this.telemetry = telemetry; this.clock = clock;
    }

    public Review submit(TenantAccessContext access, String bookingId, int rating, String comment) {
        customer(access);
        String id = identifier(bookingId);
        if (rating < 1 || rating > 5 || (comment != null && comment.length() > 1000))
            throw new BusinessRuleViolationException("Rating must be 1-5 and comment at most 1000 characters");
        String reviewId = UUID.randomUUID().toString();
        AuditCommand failure = event(access, AuditAction.FEEDBACK_SUBMITTED, null, "REVIEW", reviewId);
        final BookingReviewEligibility[] eligible = new BookingReviewEligibility[1];
        final String[] business = new String[1];
        return telemetry.observe(Telemetry.Operation.FEEDBACK_SUBMIT, () -> audit.executeDeferred(() -> {
            locks.acquire(MutationLock.booking(id));
            eligible[0] = bookings.findReviewEligibility(id, access.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
            BranchSnapshot branch = marketplace.findBranchOptional(eligible[0].branchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
            business[0] = branch.businessId();
            if (eligible[0].status() != BookingStatus.COMPLETED)
                throw new BusinessRuleViolationException("Only completed bookings can be reviewed");
            if (eligible[0].scheduledAt().isAfter(LocalDateTime.ofInstant(clock.instant(), ZoneId.of(branch.timezone()))))
                throw new BusinessRuleViolationException("Future bookings cannot be reviewed");
            return event(access, AuditAction.FEEDBACK_SUBMITTED, business[0], "REVIEW", reviewId);
        }, failure, () -> {
            var booking = eligible[0];
            Review review = new Review(reviewId, booking.bookingId(), access.userId(), business[0],
                    booking.branchId(), booking.offeringId(), booking.serviceId(), rating, comment,
                    clock.instant(), PublicationState.PUBLISHED, null, null, null);
            if (!repository.insert(review)) throw new ResourceConflictException("Booking already has a review");
            return review;
        }));
    }

    public Review ownReview(TenantAccessContext access, String bookingId) {
        customer(access);
        return transactions.read(() -> {
            var booking = bookings.findReviewEligibility(identifier(bookingId), access.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
            return repository.byBooking(booking.bookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        });
    }

    public PageResult<Review> tenantReviews(TenantAccessContext access, String requested, ListQuery query) {
        ownerOrAdmin(access);
        return transactions.readSnapshot(() -> repository.reviews(query.scoped(
                new ListScope(business(access, requested), null, null, false))));
    }

    public RatingSummary branchSummary(String branchId) {
        return transactions.readSnapshot(() -> {
            BranchSnapshot branch = marketplace.findBranchOptional(identifier(branchId))
                    .filter(BranchSnapshot::discoverable).orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
            return RatingSummary.from(repository.distribution(branch.branchId(), null));
        });
    }

    public RatingSummary offeringSummary(String offeringId) {
        return transactions.readSnapshot(() -> {
            var offering = offerings.findOfferingOptional(identifier(offeringId))
                    .filter(o -> o.discoverable() && marketplace.findBranchOptional(o.branchId())
                            .filter(BranchSnapshot::discoverable).isPresent())
                    .orElseThrow(() -> new ResourceNotFoundException("Offering not found"));
            return RatingSummary.from(repository.distribution(null, offering.offeringId()));
        });
    }

    public ReviewReport report(TenantAccessContext access, String reviewId, String reason) {
        String id = identifier(reviewId); String text = reason(reason);
        String reportId = UUID.randomUUID().toString();
        return telemetry.observe(Telemetry.Operation.FEEDBACK_REPORT, () -> audit.execute(
                event(access, AuditAction.FEEDBACK_REPORTED, null, "REVIEW", id), () -> {
            Review review = repository.byId(id).orElseThrow(() -> new ResourceNotFoundException("Review not found"));
            if (!(access.isCustomer() && access.userId().equals(review.customerId()))
                    && !(access.role() == com.carwash.identity.domain.RoleName.BUSINESS_OWNER
                         && access.requireBusinessId().equals(review.businessId())))
                throw new ResourceNotFoundException("Review not found");
            ReviewReport report = new ReviewReport(reportId, id, review.businessId(), access.userId(), text,
                    clock.instant(), ReportState.OPEN, null, null, null);
            if (!repository.insertReport(report)) throw new ResourceConflictException("Review already reported by actor");
            return report;
        }));
    }

    public PageResult<ReviewReport> moderationQueue(TenantAccessContext access, String requested, ListQuery query) {
        access.requirePlatformAdministrator();
        return transactions.readSnapshot(() -> repository.reports(query.scoped(
                new ListScope(business(access, requested), null, null, false))));
    }

    public Review moderate(TenantAccessContext access, String reviewId, PublicationState state, String reason) {
        access.requirePlatformAdministrator(); String id = identifier(reviewId); String text = reason(reason);
        AuditAction action = state == PublicationState.HIDDEN ? AuditAction.FEEDBACK_HIDDEN : AuditAction.FEEDBACK_RESTORED;
        return telemetry.observe(Telemetry.Operation.FEEDBACK_MODERATE, () -> audit.execute(
                event(access, action, null, "REVIEW", id), () -> {
            locks.acquire(MutationLock.feedbackReview(id));
            Review review = repository.byId(id).orElseThrow(() -> new ResourceNotFoundException("Review not found"));
            if (review.publication() == state) throw new ResourceConflictException("Review already has that publication state");
            Review changed = review.moderate(state, text, access.userId(), clock.instant());
            repository.update(changed); return changed;
        }));
    }

    public ReviewReport resolve(TenantAccessContext access, String reportId, String reason) {
        access.requirePlatformAdministrator(); String id = identifier(reportId); String text = reason(reason);
        return telemetry.observe(Telemetry.Operation.FEEDBACK_MODERATE, () -> audit.execute(
                event(access, AuditAction.FEEDBACK_REPORT_RESOLVED, null, "REPORT", id), () -> {
            locks.acquire(MutationLock.feedbackReport(id));
            ReviewReport report = repository.reportById(id).orElseThrow(() -> new ResourceNotFoundException("Report not found"));
            if (report.state() == ReportState.RESOLVED) throw new ResourceConflictException("Report already resolved");
            ReviewReport changed = report.resolve(text, access.userId(), clock.instant());
            repository.updateReport(changed); return changed;
        }));
    }

    private String business(TenantAccessContext access, String requested) {
        if (requested == null || requested.isBlank()) throw new BusinessRuleViolationException("businessId is required");
        String id = identifier(requested);
        if (!access.isPlatformAdministrator() && !id.equals(access.requireBusinessId()))
            throw new ResourceNotFoundException("Business not found");
        return marketplace.findBusinessOptional(id).map(BusinessSnapshot::businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
    }
    private static void customer(TenantAccessContext access) {
        if (!access.isCustomer()) throw new AccessDeniedException("Customer access is required");
    }
    private static void ownerOrAdmin(TenantAccessContext access) {
        if (!access.isPlatformAdministrator() && access.role() != com.carwash.identity.domain.RoleName.BUSINESS_OWNER)
            throw new AccessDeniedException("Owner or administrator access is required");
    }
    private static String identifier(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 64)
            throw new BusinessRuleViolationException("Invalid identifier");
        return value.trim();
    }
    private static String reason(String value) {
        if (value == null || value.isBlank() || value.length() > 500)
            throw new BusinessRuleViolationException("Reason must contain 1-500 characters");
        return value;
    }
    private static AuditCommand event(TenantAccessContext access, AuditAction action, String business,
            String type, String id) {
        return AuditCommand.actionForBusiness(action, AuditActor.user(access.userId(), access.canonicalRoleName(),
                access.businessId()), business, type, id, AuditSource.API);
    }
}
