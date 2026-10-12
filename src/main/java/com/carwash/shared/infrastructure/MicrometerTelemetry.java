package com.carwash.shared.infrastructure;

import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.application.Telemetry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;

import java.util.EnumSet;
import java.util.Locale;
import java.util.function.Supplier;

/** Explicit calls instrument final/manual beans; no proxy or annotation interception. */
public final class MicrometerTelemetry implements Telemetry {
    private final MeterRegistry meters;
    private final Tracer tracer;
    private final ObjectProvider<DataTransactionOperations> transactions;
    private final ThreadLocal<EnumSet<Operation>> active = new ThreadLocal<>();

    public MicrometerTelemetry(MeterRegistry meters, Tracer tracer,
                               ObjectProvider<DataTransactionOperations> transactions) {
        this.meters = meters;
        this.tracer = tracer;
        this.transactions = transactions;
    }

    @Override
    public <T> T observe(Operation operation, Supplier<T> action) {
        EnumSet<Operation> current = active.get();
        if (current != null && current.contains(operation)) return action.get();
        if (current == null) { current = EnumSet.noneOf(Operation.class); active.set(current); }
        current.add(operation);
        String name = operation.name().toLowerCase(Locale.ROOT);
        boolean persistence = operation == Operation.PERSISTENCE_READ || operation == Operation.PERSISTENCE_WRITE;
        if (!persistence) safely(() -> meters.counter("carwash.workflow.attempts", "operation", name).increment());
        long started = System.nanoTime();
        Span span = startSpan(name);
        Tracer.SpanInScope scope = null;
        String outcome = "returned";
        try {
            if (span != null) {
                try { scope = tracer.withSpan(span); } catch (RuntimeException unavailable) { /* best effort */ }
            }
            T result = action.get();
            if (!persistence) safely(() -> transactions.getObject().afterCompletion(committed ->
                    safely(() -> meters.counter("carwash.workflow.outcomes", "operation", name,
                            "outcome", committed ? "committed" : "rollback").increment())));
            return result;
        } catch (RuntimeException | Error failure) {
            outcome = "failed";
            if (!persistence) safely(() -> meters.counter("carwash.workflow.outcomes", "operation", name,
                    "outcome", "failed").increment());
            if (span != null) {
                Span failedSpan = span;
                safely(() -> failedSpan.tag("outcome", "failed"));
                safely(() -> failedSpan.error(new IllegalStateException("OPERATION_FAILED")));
                // No original Throwable: exporters must never see exception messages or causes.
            }
            throw failure;
        } finally {
            if (scope != null) { Tracer.SpanInScope opened = scope; safely(opened::close); }
            if (span != null) safely(span::end);
            String terminal = outcome;
            if (!persistence) safely(() -> Timer.builder("carwash.workflow.duration")
                    .tags("operation", name, "outcome", terminal).publishPercentileHistogram()
                    .register(meters).record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS));
            current.remove(operation);
            if (current.isEmpty()) active.remove();
        }
    }

    @Override public void candidates(int count) {
        safely(() -> meters.summary("carwash.recommendation.candidates").record(Math.max(0, count)));
    }

    private Span startSpan(String operation) {
        try { return tracer.nextSpan().name("carwash." + operation).tag("operation", operation).start(); }
        catch (RuntimeException unavailable) { return null; }
    }

    private static void safely(Runnable action) {
        try { action.run(); } catch (RuntimeException ignored) { /* observability cannot change business outcomes */ }
    }
}
