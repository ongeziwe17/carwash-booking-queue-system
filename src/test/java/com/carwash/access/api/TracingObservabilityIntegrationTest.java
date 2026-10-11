package com.carwash.access.api;

import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.shared.infrastructure.PrivacySpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureTracing(export = true)
@Import(TracingObservabilityIntegrationTest.Configuration.class)
@TestPropertySource(properties = "management.tracing.sampling.probability=1.0")
class TracingObservabilityIntegrationTest extends ApiIntegrationTestSupport {
    @Autowired Sink sink;
    @Autowired SdkTracerProvider provider;
    @Autowired io.micrometer.tracing.Tracer tracer;

    @Test void manuallyComposedFinalBookingServiceCreatesApplicationAndPersistenceSpans() throws Exception {
        sink.spans.clear();
        new com.carwash.testsupport.BookingApiFixture(api, ids)
                .createBooking(com.carwash.testsupport.TestDates.futureDays(100));
        assertTrue(provider.forceFlush().join(2, TimeUnit.SECONDS).isSuccess());
        var booking = sink.spans.stream().filter(s -> s.getName().equals("carwash.booking_create"))
                .findFirst().orElseThrow();
        assertTrue(sink.spans.stream().anyMatch(s -> s.getName().equals("carwash.persistence_write")
                && s.getTraceId().equals(booking.getTraceId()) && s.getParentSpanId().equals(booking.getSpanId())));
        assertTrue(sink.spans.stream().anyMatch(s -> s.getName().equals("http.server")
                && s.getTraceId().equals(booking.getTraceId())));
        assertNull(tracer.currentSpan());
        assertNull(org.slf4j.MDC.get("correlationId"));
    }

    @Test void incomingTraceParentReachesPersistenceAndExportContainsNoUrlOrExceptionText() throws Exception {
        sink.spans.clear();
        String traceId = "12345678901234567890123456789012";
        mockMvc.perform(get("/api/bookings/private-booking-sentinel").with(authentication.platformAdminJwt())
                        .header("traceparent", "00-" + traceId + "-1234567890123456-01")
                        .queryParam("secret", "private-email@example.test"))
                .andExpect(status().isNotFound());
        assertTrue(provider.forceFlush().join(2, TimeUnit.SECONDS).isSuccess());
        var spans = sink.spans.stream().filter(s -> s.getTraceId().equals(traceId)).toList();
        assertEquals(1, spans.stream().filter(s -> s.getName().equals("http.server")).count());
        assertEquals(1, spans.stream().filter(s -> s.getName().equals("carwash.persistence_read")).count());
        var http = spans.stream().filter(s -> s.getName().equals("http.server")).findFirst().orElseThrow();
        var read = spans.stream().filter(s -> s.getName().equals("carwash.persistence_read")).findFirst().orElseThrow();
        var parents = spans.stream().collect(java.util.stream.Collectors.toMap(SpanData::getSpanId, SpanData::getParentSpanId));
        String ancestor = read.getParentSpanId();
        for (int i = 0; i < spans.size() && !ancestor.equals(http.getSpanId()); i++)
            ancestor = parents.getOrDefault(ancestor, "");
        assertEquals(http.getSpanId(), ancestor);
        assertEquals("1234567890123456", http.getParentSpanId());
        for (var span : spans) {
            assertFalse(span.getAttributes().toString().contains("private-"));
            assertTrue(span.getEvents().isEmpty());
            assertTrue(span.getStatus().getDescription().isEmpty());
        }
        assertNull(tracer.currentSpan());
        assertNull(org.slf4j.MDC.get("traceId"));
        assertNull(org.slf4j.MDC.get("correlationId"));
    }

    static class Sink {
        final CopyOnWriteArrayList<SpanData> spans = new CopyOnWriteArrayList<>();
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean Sink sink() { return new Sink(); }
        @Bean @Primary SpanExporter capturedExporter(Sink sink) {
            return new PrivacySpanExporter(new SpanExporter() {
                @Override public CompletableResultCode export(Collection<SpanData> spans) { sink.spans.addAll(spans); return CompletableResultCode.ofSuccess(); }
                @Override public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
                @Override public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
            });
        }
    }
}
