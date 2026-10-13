package com.carwash.feedback.domain;

import com.carwash.shared.application.ListQuery;
import com.carwash.shared.application.PageResult;
import java.util.Optional;

/** Module-owned storage; insert methods return false for unique booking/actor conflicts. */
public interface FeedbackRepository {
    boolean insert(Review review);
    Optional<Review> byBooking(String bookingId);
    Optional<Review> byId(String id);
    PageResult<Review> reviews(ListQuery query);
    long[] distribution(String branchId, String offeringId);
    void update(Review review);
    boolean insertReport(ReviewReport report);
    Optional<ReviewReport> reportById(String id);
    PageResult<ReviewReport> reports(ListQuery query);
    void updateReport(ReviewReport report);
    boolean referencesUser(String userId);
}
