package com.carwash.access.infrastructure;

import com.carwash.access.application.TenantAccessContext;
import com.carwash.access.application.TenantAccessContextProvider;
import com.carwash.audit.application.*;
import com.carwash.audit.domain.AuditAction;
import com.carwash.audit.domain.AuditOutcome;
import com.carwash.audit.domain.AuditSource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/** Records terminal resource-server failures without inspecting invalid bearer contents. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class AuditRequestFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditRequestFilter.class);
    private final AuditOperations audit;
    private final TenantAccessContextProvider contexts;

    public AuditRequestFilter(AuditOperations audit, TenantAccessContextProvider contexts) {
        this.audit = audit;
        this.contexts = contexts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = AuditRequestContext.open();
        MDC.put("correlationId", correlationId);
        response.setHeader("X-Correlation-ID", correlationId);
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
            recordTerminalFailure(request, response);
        } finally {
            try {
                if (!request.getRequestURI().startsWith("/actuator")) {
                    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                    String route = pattern instanceof String value && value.startsWith("/api/")
                            && value.length() < 160 ? value : "unmatched";
                    LOGGER.info("request_completed route={} status={}", route, completed ? response.getStatus() : 500);
                }
            } catch (RuntimeException loggingFailure) {
                // Operational logging must not mask the request's original result/failure.
            } finally {
                MDC.remove("correlationId");
                AuditRequestContext.close();
            }
        }
    }

    private void recordTerminalFailure(HttpServletRequest request, HttpServletResponse response) {
        if (!request.getRequestURI().startsWith("/api/") || AuditRequestContext.hasRecorded()) return;
        try {
            if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                String reason = request.getHeader("Authorization") == null ? "MISSING_BEARER" : "INVALID_BEARER";
                audit.appendIsolated(new AuditCommand(AuditAction.BEARER_AUTHENTICATION,
                                AuditAction.BEARER_AUTHENTICATION, AuditActor.anonymous(), null, "API_ROUTE", null,
                                AuditSource.SECURITY, Map.of("httpMethod", request.getMethod(), "category", "API")),
                        AuditOutcome.DENIED, reason);
            } else if (response.getStatus() == HttpServletResponse.SC_FORBIDDEN) {
                audit.appendIsolated(AuditCommand.action(AuditAction.AUTHORIZATION_DENIED, actor(),
                                "API_ROUTE", null, AuditSource.SECURITY), AuditOutcome.DENIED, "ACCESS_DENIED");
            }
        } catch (RuntimeException persistenceFailure) {
            LOGGER.error("audit_security_event_persistence_failure status={} correlationId={}",
                    response.getStatus(), AuditRequestContext.correlationId());
        }
    }

    private AuditActor actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            TenantAccessContext context = contexts.from(authentication);
            return AuditActor.user(context.userId(), context.role().name(), context.businessId());
        } catch (RuntimeException invalid) {
            return AuditActor.anonymous();
        }
    }
}
