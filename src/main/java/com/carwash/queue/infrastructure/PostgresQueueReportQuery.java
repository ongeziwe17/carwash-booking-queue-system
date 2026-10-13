package com.carwash.queue.infrastructure;

import com.carwash.queue.application.QueueReportBucket;
import com.carwash.queue.application.QueueReportQuery;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Component @Profile("postgres")
public class PostgresQueueReportQuery implements QueueReportQuery {
    private final NamedParameterJdbcTemplate jdbc;
    public PostgresQueueReportQuery(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public List<QueueReportBucket> events(String branchId, LocalDate start, LocalDate end,
                                                      ZoneId branchZone, ZoneId sourceZone) {
        var boundsStart = start.atStartOfDay(branchZone).withZoneSameInstant(sourceZone).toLocalDateTime();
        var boundsEnd = end.atStartOfDay(branchZone).withZoneSameInstant(sourceZone).toLocalDateTime();
        var params = new MapSqlParameterSource().addValue("branch",branchId)
                .addValue("start",Timestamp.valueOf(boundsStart.minusDays(1)))
                .addValue("end",Timestamp.valueOf(boundsEnd.plusDays(1)))
                .addValue("source",sourceZone.getId()).addValue("destination",branchZone.getId());
        // Each lifecycle timestamp is an independent event; do not join bookings or count status twice.
        return jdbc.query("""
                WITH events AS (
                  SELECT joined_at AS event_at, 'JOINED' AS kind, NULL::timestamp AS prior
                  FROM queue_entries WHERE branch_id=:branch AND joined_at>=:start AND joined_at<:end
                  UNION ALL
                  SELECT started_at, 'STARTED', joined_at
                  FROM queue_entries WHERE branch_id=:branch AND started_at>=:start AND started_at<:end
                  UNION ALL
                  SELECT completed_at, 'COMPLETED', started_at
                  FROM queue_entries WHERE branch_id=:branch AND completed_at>=:start AND completed_at<:end
                ), localized AS (
                  SELECT (event_at AT TIME ZONE :source AT TIME ZONE :destination) AS local_at, kind,
                    CASE WHEN prior IS NOT NULL
                                   AND prior = ((prior AT TIME ZONE :source) AT TIME ZONE :source)
                                   AND (event_at AT TIME ZONE :source) >= (prior AT TIME ZONE :source)
                         THEN FLOOR(EXTRACT(EPOCH FROM ((event_at AT TIME ZONE :source) - (prior AT TIME ZONE :source))))::bigint
                         ELSE NULL END AS elapsed
                  FROM events WHERE event_at = ((event_at AT TIME ZONE :source) AT TIME ZONE :source)
                )
                SELECT local_at::date AS day, EXTRACT(HOUR FROM local_at)::integer AS hour, kind,
                       count(*) AS volume, count(elapsed) AS samples,
                       coalesce(sum(elapsed),0)::bigint AS seconds, max(elapsed) AS maximum
                FROM localized WHERE local_at::date>=:firstDay AND local_at::date<:lastDay
                GROUP BY day, hour, kind
                """, new MapSqlParameterSource(params.getValues()).addValue("firstDay",start)
                        .addValue("lastDay",end), (rs,row) -> new QueueReportBucket(
                rs.getDate("day").toLocalDate(),rs.getInt("hour"),QueueReportBucket.Event.valueOf(rs.getString("kind")),
                rs.getLong("volume"),rs.getLong("samples"),rs.getLong("seconds"),
                rs.getObject("maximum") == null ? null : rs.getLong("maximum")));
    }
}
