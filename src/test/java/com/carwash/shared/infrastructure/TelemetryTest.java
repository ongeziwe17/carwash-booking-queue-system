package com.carwash.shared.infrastructure;

import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.application.Telemetry.Operation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TelemetryTest {
    @Test void countsCommittedRollbackAndThrownFailuresWithoutNestedDoubleCounting() {
        var transactions = new InMemoryDataCoordinator();
        var meters = new SimpleMeterRegistry();
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("transactions", transactions);
        var telemetry = new MicrometerTelemetry(meters, io.micrometer.tracing.Tracer.NOOP,
                beans.getBeanProvider(DataTransactionOperations.class));
        telemetry.observe(Operation.BOOKING_CREATE, () -> telemetry.observe(Operation.BOOKING_CREATE, () -> "created"));
        assertEquals(1, counter(meters, "committed"));
        var original = new IllegalStateException("customer@example.test JWT-secret special-request");
        assertSame(original, assertThrows(IllegalStateException.class, () -> transactions.write(() -> {
            telemetry.observe(Operation.BOOKING_CREATE, () -> "provisional");
            assertEquals(1, counter(meters, "committed"));
            throw original;
        })));
        assertEquals(1, counter(meters, "rollback"));
        assertEquals(1, counter(meters, "committed"));
        assertSame(original, assertThrows(IllegalStateException.class,
                () -> telemetry.observe(Operation.BOOKING_CREATE, () -> { throw original; })));
        assertEquals(1, counter(meters, "failed"));
        assertEquals(3, meters.get("carwash.workflow.attempts").tag("operation", "booking_create").counter().count());
        telemetry.candidates(7);
        assertEquals(7, meters.get("carwash.recommendation.candidates").summary().totalAmount());
        transactions.write(() -> transactions.afterCompletion(committed -> { throw new IllegalStateException(); }));
    }

    @Test void realSpansHaveParentRelationshipsSafeErrorsAndNoLeakedContext() {
        List<SpanData> exported = new ArrayList<>();
        SpanExporter collector = new SpanExporter() {
            @Override public CompletableResultCode export(Collection<SpanData> spans) { exported.addAll(spans); return CompletableResultCode.ofSuccess(); }
            @Override public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
            @Override public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
        };
        try (var provider = SdkTracerProvider.builder().setSampler(Sampler.alwaysOn())
                .addSpanProcessor(SimpleSpanProcessor.create(new PrivacySpanExporter(collector))).build()) {
            var tracer = new OtelTracer(provider.get("controlled-test"), new OtelCurrentTraceContext(), event -> { });
            var beans = new DefaultListableBeanFactory();
            beans.registerSingleton("transactions", new InMemoryDataCoordinator());
            var telemetry = new MicrometerTelemetry(new SimpleMeterRegistry(), tracer,
                    beans.getBeanProvider(DataTransactionOperations.class));
            assertThrows(IllegalStateException.class, () -> telemetry.observe(Operation.QUEUE_START,
                    () -> telemetry.observe(Operation.PERSISTENCE_WRITE, () -> {
                        throw new IllegalStateException("password JWT email@example.test plate AB123");
                    })));
            assertNull(tracer.currentSpan());
            assertEquals(2, exported.size());
            SpanData child = exported.getFirst(), parent = exported.getLast();
            assertEquals(parent.getSpanId(), child.getParentSpanId());
            assertEquals(parent.getTraceId(), child.getTraceId());
            assertEquals("failed", parent.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("outcome")));
            for (var span : exported) {
                assertTrue(span.getEvents().isEmpty());
                assertTrue(span.getStatus().getDescription().isEmpty());
                assertFalse(span.getAttributes().toString().contains("email@example.test"));
                assertEquals("hlambaq-api", span.getResource().getAttribute(
                        io.opentelemetry.api.common.AttributeKey.stringKey("service.name")));
            }
            var raw = provider.get("controlled-test").spanBuilder("/api/customer@example.test?token=secret").startSpan();
            raw.setAttribute("url.full", "https://host/?password=secret");
            raw.setAttribute("db.statement", "select email from users");
            raw.setAttribute("user.id", "customer@example.test");
            raw.recordException(new RuntimeException("secret-provider-key"));
            raw.end();
            var sanitized = exported.getLast();
            assertEquals("framework.internal", sanitized.getName());
            assertEquals(0, sanitized.getAttributes().size());
            assertTrue(sanitized.getEvents().isEmpty());
        }
    }

    @Test void disabledAndBrokenExportAreNonThrowing() {
        assertTrue(new PrivacySpanExporter(null).export(List.of()).isSuccess());
        assertTrue(new PrivacySpanExporter(null).flush().isSuccess());
        assertTrue(new PrivacySpanExporter(null).shutdown().isSuccess());
        var broken = new PrivacySpanExporter(new SpanExporter() {
            @Override public CompletableResultCode export(Collection<SpanData> spans) { throw new IllegalStateException("collector down"); }
            @Override public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
            @Override public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
        });
        assertFalse(broken.export(List.of()).isSuccess());
    }

    private static double counter(SimpleMeterRegistry meters, String outcome) {
        var counter = meters.find("carwash.workflow.outcomes").tags("operation", "booking_create", "outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }
}
