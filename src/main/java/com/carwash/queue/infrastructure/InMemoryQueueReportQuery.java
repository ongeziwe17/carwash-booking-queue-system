package com.carwash.queue.infrastructure;

import com.carwash.queue.application.QueueReportBucket;
import com.carwash.queue.application.QueueReportQuery;
import com.carwash.queue.domain.QueueEntryRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

@Component @Profile("!postgres")
public class InMemoryQueueReportQuery implements QueueReportQuery {
    private final QueueEntryRepository entries;
    public InMemoryQueueReportQuery(QueueEntryRepository entries) { this.entries = entries; }

    /** PostgreSQL resolves an ambiguous timestamp with the later (standard) offset. */
    private static Instant instant(LocalDateTime value, ZoneId source) {
        if (value == null) return null;
        var offsets = source.getRules().getValidOffsets(value);
        return offsets.isEmpty() ? null : value.atOffset(offsets.getLast()).toInstant();
    }
    private record Key(LocalDate date, int hour, QueueReportBucket.Event event) { }
    private static final class Samples {
        long count, samples, seconds;
        Long maximum;
        void add(Long elapsed) {
            count++;
            if (elapsed != null && elapsed >= 0) {
                samples++; seconds += elapsed; maximum = maximum == null ? elapsed : Math.max(maximum, elapsed);
            }
        }
    }
    @Override public List<QueueReportBucket> events(String branchId, LocalDate start, LocalDate end,
                                                      ZoneId branchZone, ZoneId sourceZone) {
        Map<Key,Samples> buckets = new HashMap<>();
        for (var entry : entries.findByBranchId(branchId)) {
            Instant joined = instant(entry.getJoinedAt(), sourceZone);
            Instant started = instant(entry.getStartedAt(), sourceZone);
            Instant completed = instant(entry.getCompletedAt(), sourceZone);
            add(buckets, joined, null, QueueReportBucket.Event.JOINED, start, end, branchZone);
            add(buckets, started, joined == null || started == null ? null : Duration.between(joined,started).getSeconds(),
                    QueueReportBucket.Event.STARTED, start, end, branchZone);
            add(buckets, completed, started == null || completed == null ? null : Duration.between(started,completed).getSeconds(),
                    QueueReportBucket.Event.COMPLETED, start, end, branchZone);
        }
        return buckets.entrySet().stream().map(e -> new QueueReportBucket(e.getKey().date(),e.getKey().hour(),
                e.getKey().event(),e.getValue().count,e.getValue().samples,e.getValue().seconds,
                e.getValue().maximum)).toList();
    }
    private static void add(Map<Key,Samples> buckets, Instant at, Long elapsed, QueueReportBucket.Event event,
                            LocalDate start, LocalDate end, ZoneId branchZone) {
        if (at == null) return;
        var local = at.atZone(branchZone);
        if (local.toLocalDate().isBefore(start) || !local.toLocalDate().isBefore(end)) return;
        buckets.computeIfAbsent(new Key(local.toLocalDate(), local.getHour(), event), k -> new Samples()).add(elapsed);
    }
}
