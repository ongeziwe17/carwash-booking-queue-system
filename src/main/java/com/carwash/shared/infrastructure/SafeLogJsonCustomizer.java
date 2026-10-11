package com.carwash.shared.infrastructure;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

import java.util.Set;

/** Fail-closed export policy for Boot's Logstash JSON formatter, including framework errors. */
public final class SafeLogJsonCustomizer implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {
    private static final Set<String> FIELDS = Set.of("@timestamp", "level", "logger_name", "thread_name",
            "correlationId", "traceId", "spanId", "safe_event", "exception_type", "stack_location");
    private static final Set<String> EVENTS = Set.of("request_completed", "api_failure", "audit_event",
            "audit_persistence_failure", "audit_structured_log_failure", "notification_creation_failed",
            "audit_security_event_persistence_failure", "audit_login_failure_persistence_failure");

    @Override public void customize(JsonWriter.Members<ILoggingEvent> members) {
        // JsonWriter path filters return true for members to exclude.
        members.applyingPathFilter(path -> !FIELDS.contains(path.name()));
        members.add("safe_event", event -> {
            String template = event.getMessage();
            String code = template == null ? "framework_event" : template.split(" ", 2)[0];
            return event.getLoggerName().startsWith("com.carwash.") && EVENTS.contains(code)
                    ? event.getFormattedMessage() : "framework_event";
        });
        members.add("exception_type", event -> event.getThrowableProxy() == null ? null
                : event.getThrowableProxy().getClassName()).whenNotNull();
        members.add("stack_location", event -> {
            if (event.getThrowableProxy() == null) return null;
            var frames = event.getThrowableProxy().getStackTraceElementProxyArray();
            if (frames != null) for (var proxy : frames) {
                var frame = proxy.getStackTraceElement();
                if (frame.getClassName().startsWith("com.carwash."))
                    return frame.getClassName() + ":" + frame.getLineNumber();
            }
            return "framework";
        }).whenNotNull();
    }
}
