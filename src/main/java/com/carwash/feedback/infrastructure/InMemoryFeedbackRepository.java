package com.carwash.feedback.infrastructure;

import com.carwash.feedback.domain.*;
import com.carwash.shared.application.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryFeedbackRepository implements FeedbackRepository {
    private final Map<String, Review> reviews = new ConcurrentHashMap<>();
    private final Map<String, String> bookings = new ConcurrentHashMap<>();
    private final Map<String, ReviewReport> reports = new ConcurrentHashMap<>();
    private final Map<String, String> reporters = new ConcurrentHashMap<>();
    private final DataTransactionOperations transactions;

    public InMemoryFeedbackRepository(DataTransactionOperations transactions) { this.transactions = transactions; }

    public void clearForTests() { reports.clear(); reporters.clear(); reviews.clear(); bookings.clear(); }

    @Override public boolean insert(Review review) {
        if (bookings.putIfAbsent(review.bookingId(), review.id()) != null) return false;
        reviews.put(review.id(), review);
        transactions.onRollback(() -> { reviews.remove(review.id()); bookings.remove(review.bookingId(), review.id()); });
        return true;
    }
    @Override public Optional<Review> byBooking(String id) {
        return Optional.ofNullable(bookings.get(id)).map(reviews::get);
    }
    @Override public Optional<Review> byId(String id) { return Optional.ofNullable(reviews.get(id)); }
    @Override public PageResult<Review> reviews(ListQuery query) {
        String business = query.scope().businessId();
        return query.select(reviews.values().stream().filter(r -> business.equals(r.businessId())).toList(),
                Map.of("id", Review::id, "createdAt", Review::createdAt, "rating", r -> String.valueOf(r.rating()),
                        "branchId", Review::branchId, "offeringId", Review::offeringId,
                        "publication", r -> r.publication().name()));
    }
    @Override public long[] distribution(String branchId, String offeringId) {
        long[] counts = new long[5];
        reviews.values().stream().filter(r -> r.publication() == PublicationState.PUBLISHED)
                .filter(r -> branchId == null || branchId.equals(r.branchId()))
                .filter(r -> offeringId == null || offeringId.equals(r.offeringId()))
                .forEach(r -> counts[r.rating() - 1]++);
        return counts;
    }
    @Override public void update(Review review) {
        Review previous = reviews.put(review.id(), review);
        transactions.onRollback(() -> reviews.put(review.id(), previous));
    }
    @Override public boolean insertReport(ReviewReport report) {
        String key = report.reviewId() + ":" + report.actorId();
        if (reporters.putIfAbsent(key, report.id()) != null) return false;
        reports.put(report.id(), report);
        transactions.onRollback(() -> { reports.remove(report.id()); reporters.remove(key, report.id()); });
        return true;
    }
    @Override public Optional<ReviewReport> reportById(String id) { return Optional.ofNullable(reports.get(id)); }
    @Override public PageResult<ReviewReport> reports(ListQuery query) {
        String business = query.scope().businessId();
        return query.select(reports.values().stream().filter(r -> business.equals(r.businessId())).toList(),
                Map.of("id", ReviewReport::id, "createdAt", ReviewReport::createdAt,
                        "state", r -> r.state().name()));
    }
    @Override public void updateReport(ReviewReport report) {
        ReviewReport previous = reports.put(report.id(), report);
        transactions.onRollback(() -> reports.put(report.id(), previous));
    }
    @Override public boolean referencesUser(String userId) {
        return reviews.values().stream().anyMatch(r -> userId.equals(r.moderatorId()))
                || reports.values().stream().anyMatch(r -> userId.equals(r.actorId())
                        || userId.equals(r.resolvedBy()));
    }
}
