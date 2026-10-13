package com.carwash.feedback.infrastructure;

import com.carwash.feedback.domain.*;
import com.carwash.shared.application.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class PostgresFeedbackRepository implements FeedbackRepository {
    private final JdbcTemplate jdbc;
    public PostgresFeedbackRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public boolean insert(Review r) {
        return jdbc.update("""
                INSERT INTO feedback_reviews(review_id,booking_id,customer_id,business_id,branch_id,offering_id,
                    service_id,rating,comment,created_at,publication_state)
                VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (booking_id) DO NOTHING
                """, r.id(),r.bookingId(),r.customerId(),r.businessId(),r.branchId(),r.offeringId(),
                r.serviceId(),r.rating(),r.comment(),Timestamp.from(r.createdAt()),r.publication().name()) == 1;
    }
    @Override public Optional<Review> byBooking(String id) {
        return jdbc.query("SELECT * FROM feedback_reviews WHERE booking_id=?", this::review, id).stream().findFirst();
    }
    @Override public Optional<Review> byId(String id) {
        return jdbc.query("SELECT * FROM feedback_reviews WHERE review_id=?", this::review, id).stream().findFirst();
    }
    @Override public PageResult<Review> reviews(ListQuery query) {
        SqlFilter filter = filter(query, false);
        long total = jdbc.queryForObject("SELECT count(*) FROM feedback_reviews WHERE " + filter.where(),
                Long.class, filter.args().toArray());
        List<Object> args = new ArrayList<>(filter.args());
        args.add(query.page().size()); args.add(query.page().offset());
        List<Review> content = jdbc.query("SELECT * FROM feedback_reviews WHERE " + filter.where()
                + sort(query, false) + " LIMIT ? OFFSET ?", this::review, args.toArray());
        return PageResult.of(content, query.page(), total);
    }
    @Override public long[] distribution(String branchId, String offeringId) {
        String column = offeringId == null ? "branch_id" : "offering_id";
        String id = offeringId == null ? branchId : offeringId;
        long[] counts = new long[5];
        jdbc.query("SELECT rating,count(*) AS n FROM feedback_reviews WHERE publication_state='PUBLISHED' AND "
                + column + "=? GROUP BY rating", rs -> { counts[rs.getInt("rating") - 1] = rs.getLong("n"); }, id);
        return counts;
    }
    @Override public void update(Review r) {
        jdbc.update("UPDATE feedback_reviews SET publication_state=?,moderation_reason=?,moderator_id=?,moderated_at=? WHERE review_id=?",
                r.publication().name(),r.moderationReason(),r.moderatorId(),Timestamp.from(r.moderatedAt()),r.id());
    }
    @Override public boolean insertReport(ReviewReport r) {
        return jdbc.update("""
                INSERT INTO feedback_reports(report_id,review_id,business_id,actor_id,reason,created_at,report_state)
                VALUES(?,?,?,?,?,?,?) ON CONFLICT (review_id,actor_id) DO NOTHING
                """,r.id(),r.reviewId(),r.businessId(),r.actorId(),r.reason(),
                Timestamp.from(r.createdAt()),r.state().name()) == 1;
    }
    @Override public Optional<ReviewReport> reportById(String id) {
        return jdbc.query("SELECT * FROM feedback_reports WHERE report_id=?", this::report, id).stream().findFirst();
    }
    @Override public PageResult<ReviewReport> reports(ListQuery query) {
        SqlFilter filter = filter(query, true);
        long total = jdbc.queryForObject("SELECT count(*) FROM feedback_reports WHERE " + filter.where(),
                Long.class, filter.args().toArray());
        List<Object> args = new ArrayList<>(filter.args());
        args.add(query.page().size()); args.add(query.page().offset());
        return PageResult.of(jdbc.query("SELECT * FROM feedback_reports WHERE " + filter.where()
                + sort(query, true) + " LIMIT ? OFFSET ?", this::report, args.toArray()), query.page(), total);
    }
    @Override public void updateReport(ReviewReport r) {
        jdbc.update("UPDATE feedback_reports SET report_state=?,resolution_reason=?,resolved_by=?,resolved_at=? WHERE report_id=?",
                r.state().name(),r.resolutionReason(),r.resolvedBy(),Timestamp.from(r.resolvedAt()),r.id());
    }
    @Override public boolean referencesUser(String userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM feedback_reviews WHERE moderator_id=?
                    UNION ALL
                    SELECT 1 FROM feedback_reports WHERE actor_id=? OR resolved_by=?
                )
                """, Boolean.class, userId, userId, userId));
    }
    private SqlFilter filter(ListQuery query, boolean report) {
        StringBuilder where = new StringBuilder("business_id=?");
        List<Object> args = new ArrayList<>(); args.add(query.scope().businessId());
        Map<String,String> columns = report ? Map.of("state","report_state","id","report_id")
                : Map.of("branchId","branch_id","offeringId","offering_id","rating","rating",
                        "publication","publication_state","id","review_id");
        query.filters().forEach((key,value) -> {
            where.append(" AND ").append(columns.get(key)).append("=?");
            args.add(key.equals("rating") ? Integer.parseInt(value.toString()) : value);
        });
        return new SqlFilter(where.toString(),args);
    }
    private String sort(ListQuery query, boolean report) {
        Map<String,String> columns = report ? Map.of("id","report_id","createdAt","created_at")
                : Map.of("id","review_id","createdAt","created_at","rating","rating");
        return " ORDER BY " + query.sort().stream().map(s -> columns.get(s.field())
                + (s.descending() ? " DESC" : " ASC") + " NULLS LAST").collect(java.util.stream.Collectors.joining(", "));
    }
    private record SqlFilter(String where, List<Object> args) { }
    private Review review(ResultSet rs, int row) throws SQLException {
        return new Review(rs.getString("review_id"),rs.getString("booking_id"),rs.getString("customer_id"),
                rs.getString("business_id"),rs.getString("branch_id"),rs.getString("offering_id"),
                rs.getString("service_id"),rs.getInt("rating"),rs.getString("comment"),time(rs,"created_at"),
                PublicationState.valueOf(rs.getString("publication_state")),rs.getString("moderation_reason"),
                rs.getString("moderator_id"),time(rs,"moderated_at"));
    }
    private ReviewReport report(ResultSet rs, int row) throws SQLException {
        return new ReviewReport(rs.getString("report_id"),rs.getString("review_id"),rs.getString("business_id"),
                rs.getString("actor_id"),rs.getString("reason"),time(rs,"created_at"),
                ReportState.valueOf(rs.getString("report_state")),rs.getString("resolution_reason"),
                rs.getString("resolved_by"),time(rs,"resolved_at"));
    }
    private static Instant time(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
}
