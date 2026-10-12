package com.carwash.bootstrap;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ExporterConfigurationTest {
    @Test void exportIsDisabledWithoutEndpointAndUnsafeEndpointsAreRejected() {
        var config = new ObservabilityConfiguration();
        var meters = new SimpleMeterRegistry();
        try (var exporter = config.privacySpanExporter("", meters)) {
            assertTrue(exporter.export(java.util.List.of()).isSuccess());
            assertEquals(1, meters.get("carwash.trace.export.batches").tag("outcome", "disabled").counter().count());
        }
        for (String endpoint : new String[]{"http://user:password@localhost/v1/traces", "http://localhost/v1/traces?token=secret",
                "http://localhost/v1/traces#fragment", "ftp://localhost/v1/traces"})
            assertThrows(IllegalStateException.class, () -> config.privacySpanExporter(endpoint, meters));
    }

    @Test void unavailableHttpCollectorFailsInBackgroundWithoutFailingApplicationWork() {
        var config = new ObservabilityConfiguration();
        var meters = new SimpleMeterRegistry();
        try (var exporter = config.privacySpanExporter("http://127.0.0.1:9/v1/traces", meters);
             var processor = config.boundedSpanProcessor(exporter);
             var provider = SdkTracerProvider.builder().addSpanProcessor(processor).build()) {
            var span = provider.get("controlled-test").spanBuilder("carwash.booking_create").startSpan();
            assertDoesNotThrow(() -> { span.end(); });
            provider.forceFlush().join(3, TimeUnit.SECONDS);
            assertEquals(1, meters.get("carwash.trace.export.batches").tag("outcome", "failed").counter().count());
        }
    }
}
