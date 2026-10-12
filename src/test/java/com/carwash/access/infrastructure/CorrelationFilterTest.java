package com.carwash.access.infrastructure;

import com.carwash.audit.application.AuditOperations;
import com.carwash.audit.application.AuditRequestContext;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class CorrelationFilterTest {
    @Test void servletExceptionRetainsOriginalFailureAndCleansContext() {
        var filter = new AuditRequestFilter(AuditOperations.noOp(), null);
        var request = new MockHttpServletRequest("GET", "/api/failing");
        var response = new MockHttpServletResponse();
        var original = new ServletException("sensitive-original-cause");
        assertSame(original, assertThrows(ServletException.class, () -> filter.doFilter(request, response, (req, res) -> {
            assertEquals(response.getHeader("X-Correlation-ID"), MDC.get("correlationId"));
            assertEquals(response.getHeader("X-Correlation-ID"), AuditRequestContext.correlationId());
            throw original;
        })));
        assertNull(MDC.get("correlationId"));
        assertFalse(AuditRequestContext.hasRecorded());
    }
}
