package com.carwash.booking.infrastructure;

import com.carwash.booking.application.BookingReportBucket;
import com.carwash.booking.application.BookingReportQuery;
import com.carwash.booking.domain.BookingStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.StringJoiner;

@Component @Profile("postgres")
public class PostgresBookingReportQuery implements BookingReportQuery {
    private final JdbcTemplate jdbc;
    public PostgresBookingReportQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public List<BookingReportBucket> scheduledCohorts(String branchId, LocalDate start, LocalDate end) {
        return jdbc.query("""
                SELECT scheduled_at::date AS day, EXTRACT(HOUR FROM scheduled_at)::integer AS hour,
                       offering_id, booking_status, count(*) AS volume
                FROM bookings
                WHERE branch_id=? AND scheduled_at>=? AND scheduled_at<?
                GROUP BY day, hour, offering_id, booking_status
                """, (rs, row) -> new BookingReportBucket(rs.getDate("day").toLocalDate(), rs.getInt("hour"),
                rs.getString("offering_id"), BookingStatus.valueOf(rs.getString("booking_status")),
                rs.getLong("volume")), branchId, Timestamp.valueOf(start.atStartOfDay()),
                Timestamp.valueOf(end.atStartOfDay()));
    }
    @Override public BigDecimal bookedMinutes(String branchId, LocalDate day, Map<String,Integer> durations) {
        // Use the authorized, snapshot-consistent active offering projection supplied by the caller.
        if (durations.isEmpty()) return BigDecimal.ZERO.setScale(2);
        var values = new StringJoiner(",");
        List<Object> arguments = new ArrayList<>();
        durations.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            values.add("(?, ?)");
            arguments.add(entry.getKey());
            arguments.add(entry.getValue());
        });
        arguments.add(Timestamp.valueOf(day.plusDays(1).atStartOfDay()));
        arguments.add(Timestamp.valueOf(day.atStartOfDay()));
        arguments.add(branchId);
        arguments.add(Timestamp.valueOf(day.minusDays(1).atStartOfDay()));
        arguments.add(Timestamp.valueOf(day.plusDays(1).atStartOfDay()));
        arguments.add(Timestamp.valueOf(day.atStartOfDay()));
        BigDecimal seconds = jdbc.queryForObject("""
                WITH active_offerings(offering_id, minutes) AS (VALUES %s)
                SELECT coalesce(sum(EXTRACT(EPOCH FROM (
                    LEAST(b.scheduled_at + make_interval(mins => a.minutes), ?) -
                    GREATEST(b.scheduled_at, ?)))),0)
                FROM bookings b JOIN active_offerings a ON a.offering_id=b.offering_id
                WHERE b.branch_id=? AND b.booking_status<>'CANCELLED'
                  AND b.scheduled_at>=? AND b.scheduled_at<?
                  AND b.scheduled_at + make_interval(mins => a.minutes)>?
                """.formatted(values), BigDecimal.class, arguments.toArray());
        return (seconds == null ? BigDecimal.ZERO : seconds)
                .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
    }
}
