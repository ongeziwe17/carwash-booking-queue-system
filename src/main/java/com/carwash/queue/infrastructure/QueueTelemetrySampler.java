package com.carwash.queue.infrastructure;

import com.carwash.queue.application.QueueTelemetryQuery;
import com.carwash.shared.application.DataTransactionOperations;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;

/** One cached scalar aggregate per instance; scrapes perform no business queries. */
public final class QueueTelemetrySampler {
    private final QueueTelemetryQuery query;
    private final DataTransactionOperations transactions;
    private volatile QueueTelemetryQuery.State state = new QueueTelemetryQuery.State(0, 0);
    private volatile long updated;

    public QueueTelemetrySampler(QueueTelemetryQuery query, DataTransactionOperations transactions, MeterRegistry meters) {
        this.query = query;
        this.transactions = transactions;
        Gauge.builder("carwash.queue.depth", this, s -> s.updated == 0 ? Double.NaN : s.state.depth()).register(meters);
        Gauge.builder("carwash.queue.estimated.wait", this, s -> s.updated == 0 ? Double.NaN : s.state.estimatedWaitSeconds())
                .baseUnit("seconds").register(meters);
        Gauge.builder("carwash.queue.snapshot.age", this, s -> s.updated == 0 ? Double.NaN :
                Math.max(0, (System.nanoTime() - s.updated) / 1e9)).baseUnit("seconds").register(meters);
        Gauge.builder("carwash.queue.snapshot.available", this, s -> s.updated == 0 ? 0 : 1).register(meters);
    }

    @Scheduled(fixedDelayString = "${carwash.observability.queue-refresh-ms:15000}")
    public void refresh() {
        try {
            state = transactions.read(query::telemetryState);
            updated = System.nanoTime();
        } catch (RuntimeException unavailable) {
            // Preserve last known value; age alerts identify stale data. No repetitive outage logs.
        }
    }
}
