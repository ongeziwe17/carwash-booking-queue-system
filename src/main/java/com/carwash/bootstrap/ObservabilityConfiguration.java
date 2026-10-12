package com.carwash.bootstrap;

import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.application.Telemetry;
import com.carwash.shared.infrastructure.MicrometerTelemetry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.carwash.queue.application.QueueTelemetryQuery;
import com.carwash.queue.domain.QueueEntryRepository;
import com.carwash.queue.infrastructure.QueueTelemetrySampler;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ObservabilityConfiguration {
    @Bean
    io.opentelemetry.sdk.trace.export.SpanExporter privacySpanExporter(
            @org.springframework.beans.factory.annotation.Value("${carwash.observability.traces.endpoint:}") String endpoint,
            MeterRegistry meters) {
        if (endpoint.isBlank()) return new com.carwash.shared.infrastructure.PrivacySpanExporter(null, meters);
        java.net.URI uri = java.net.URI.create(endpoint);
        if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("OTLP endpoint must be HTTP(S) without credentials or query parameters");
        }
        var exporter = io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint).setConnectTimeout(java.time.Duration.ofSeconds(1))
                .setTimeout(java.time.Duration.ofSeconds(2))
                // The managed OTel sender treats null as retry disabled (a one-attempt policy).
                .setRetryPolicy(null)
                .build();
        return new com.carwash.shared.infrastructure.PrivacySpanExporter(exporter, meters);
    }

    @Bean
    io.opentelemetry.sdk.trace.export.BatchSpanProcessor boundedSpanProcessor(
            io.opentelemetry.sdk.trace.export.SpanExporter exporter) {
        return io.opentelemetry.sdk.trace.export.BatchSpanProcessor.builder(exporter)
                .setMaxQueueSize(1024).setMaxExportBatchSize(128)
                .setExporterTimeout(java.time.Duration.ofSeconds(2))
                .setScheduleDelay(java.time.Duration.ofSeconds(5)).build();
    }
    @Bean
    org.springframework.http.server.observation.ServerRequestObservationConvention safeHttpObservationConvention() {
        return new com.carwash.shared.infrastructure.SafeHttpObservationConvention();
    }
    @Bean
    Telemetry telemetry(MeterRegistry meters, ObjectProvider<Tracer> tracer,
                        ObjectProvider<DataTransactionOperations> transactions) {
        return new MicrometerTelemetry(meters, tracer.getIfAvailable(() -> Tracer.NOOP), transactions);
    }

    @Bean
    QueueTelemetrySampler queueTelemetrySampler(QueueEntryRepository repository,
            DataTransactionOperations transactions, MeterRegistry meters) {
        return new QueueTelemetrySampler((QueueTelemetryQuery) repository, transactions, meters);
    }
}
