package com.carwash.shared.infrastructure;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Final export boundary: protects automatic instrumentation as well as custom spans. */
public final class PrivacySpanExporter implements SpanExporter {
    private static final Set<String> KEYS = Set.of("operation", "outcome", "method", "uri", "status", "exception");
    private final SpanExporter delegate;
    private final io.micrometer.core.instrument.MeterRegistry meters;

    public PrivacySpanExporter(SpanExporter delegate) { this(delegate, null); }
    public PrivacySpanExporter(SpanExporter delegate, io.micrometer.core.instrument.MeterRegistry meters) {
        this.delegate = delegate;
        this.meters = meters;
    }

    @Override public CompletableResultCode export(Collection<SpanData> spans) {
        if (delegate == null) { record("disabled"); return CompletableResultCode.ofSuccess(); }
        try {
            var result = delegate.export(spans.stream().map(PrivacySpanExporter::sanitize).toList());
            result.whenComplete(() -> record(result.isSuccess() ? "succeeded" : "failed"));
            return result;
        } catch (RuntimeException unavailable) { record("failed"); return CompletableResultCode.ofFailure(); }
    }

    public static SpanData sanitize(SpanData span) {
        var safe = Attributes.builder();
        span.getAttributes().forEach((key, value) -> {
            if (KEYS.contains(key.getKey()) && value instanceof String text && text.length() <= 160
                    && text.matches("[A-Za-z0-9_/{},.:-]+")) safe.put(key.getKey(), text);
        });
        Attributes attributes = safe.build();
        return new DelegatingSpanData(span) {
            @Override public io.opentelemetry.api.trace.SpanContext getSpanContext() { return safeContext(span.getSpanContext()); }
            @Override public io.opentelemetry.api.trace.SpanContext getParentSpanContext() { return safeContext(span.getParentSpanContext()); }
            @Override public String getName() {
                String name = span.getName();
                return name.startsWith("carwash.") && name.matches("carwash\\.[a-z_]{1,40}")
                        ? name : span.getKind() == io.opentelemetry.api.trace.SpanKind.SERVER ? "http.server" : "framework.internal";
            }
            @Override public Attributes getAttributes() { return attributes; }
            @Override public List<EventData> getEvents() { return List.of(); }
            @Override public List<LinkData> getLinks() { return List.of(); }
            @Override public StatusData getStatus() { return StatusData.create(span.getStatus().getStatusCode(), ""); }
            @Override public Resource getResource() {
                return Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "carwash-booking-queue-system"));
            }
            @Override public int getTotalRecordedEvents() { return 0; }
            @Override public int getTotalRecordedLinks() { return 0; }
            @Override public int getTotalAttributeCount() { return attributes.size(); }
        };
    }

    private static io.opentelemetry.api.trace.SpanContext safeContext(io.opentelemetry.api.trace.SpanContext context) {
        return io.opentelemetry.api.trace.SpanContext.create(context.getTraceId(), context.getSpanId(),
                context.getTraceFlags(), io.opentelemetry.api.trace.TraceState.getDefault());
    }

    private void record(String outcome) {
        if (meters == null) return;
        try { meters.counter("carwash.trace.export.batches", "outcome", outcome).increment(); }
        catch (RuntimeException ignored) { /* exporter must not fail because of metrics */ }
    }

    @Override public CompletableResultCode flush() {
        return delegate == null ? CompletableResultCode.ofSuccess() : delegate.flush();
    }
    @Override public CompletableResultCode shutdown() {
        return delegate == null ? CompletableResultCode.ofSuccess() : delegate.shutdown();
    }
}
