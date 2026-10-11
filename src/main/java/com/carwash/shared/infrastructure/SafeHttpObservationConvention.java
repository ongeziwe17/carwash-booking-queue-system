package com.carwash.shared.infrastructure;

import io.micrometer.common.KeyValues;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import java.util.Set;

/** HTTP route templates only; never retain URL, query, user agent, headers, or exception text. */
public final class SafeHttpObservationConvention extends DefaultServerRequestObservationConvention {
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    @Override public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
        return KeyValues.empty();
    }
    @Override public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
        String method = context.getCarrier().getMethod();
        String route = context.getPathPattern();
        if (route == null || !(route.startsWith("/api/") || route.startsWith("/actuator/"))) route = "unmatched";
        int status = context.getResponse() == null ? 500 : context.getResponse().getStatus();
        return KeyValues.of("method", METHODS.contains(method) ? method : "OTHER", "uri", route,
                "status", Integer.toString(status), "outcome", status >= 500 ? "SERVER_ERROR"
                        : status >= 400 ? "CLIENT_ERROR" : "SUCCESS", "exception", "none");
    }
}
