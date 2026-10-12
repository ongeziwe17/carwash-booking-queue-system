package com.carwash.access.api;

import com.carwash.audit.application.AuditRequestContext;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(OutputCaptureExtension.class)
@org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics(export = true)
class ManagementObservabilityIntegrationTest extends ApiIntegrationTestSupport {
    @org.springframework.beans.factory.annotation.Autowired com.carwash.audit.domain.AuditRepository auditRecords;
    private static final String PASSWORD = "test-only-scrape-password";
    @DynamicPropertySource static void management(DynamicPropertyRegistry properties) {
        properties.add("carwash.observability.scrape.username", () -> "test-scraper");
        properties.add("carwash.observability.scrape.password-hash", () -> "{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD));
    }

    @Test void probesAreMinimalPublicAndIgnoreInvalidBearer() throws Exception {
        for (String probe : new String[]{"liveness", "readiness"}) {
            var result = mockMvc.perform(get("/actuator/health/" + probe).header("Authorization", "Bearer invalid-during-db-outage"))
                    .andExpect(status().isOk()).andExpect(content().json("{\"status\":\"UP\"}"))
                    .andExpect(jsonPath("$.components").doesNotExist()).andReturn();
            assertNotNull(result.getResponse().getHeader("X-Correlation-ID"));
        }
        assertNull(MDC.get("correlationId"));
        assertFalse(AuditRequestContext.hasRecorded());
    }

    @Test void scrapeHasDedicatedAuthenticationAndNoCustomerAccess() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer customer-token")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").with(httpBasic("test-scraper", "wrong"))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").with(httpBasic("test-scraper", PASSWORD)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")));
        for (String role : new String[]{"CUSTOMER", "STAFF", "BUSINESS_OWNER", "PLATFORM_ADMIN"})
            mockMvc.perform(get("/actuator/prometheus").with(user("business-user").roles(role))).andExpect(status().isForbidden());
        mockMvc.perform(post("/actuator/prometheus").with(httpBasic("test-scraper", PASSWORD))).andExpect(status().isForbidden());
    }

    @Test void sensitiveManagementEndpointsCannotBeExposedEvenToScraper() throws Exception {
        for (String endpoint : new String[]{"env", "configprops", "heapdump", "threaddump", "loggers", "shutdown", "metrics", "health", "info"})
            mockMvc.perform(get("/actuator/" + endpoint).with(httpBasic("test-scraper", PASSWORD))).andExpect(status().isForbidden());
        mockMvc.perform(post("/actuator/shutdown").with(httpBasic("test-scraper", PASSWORD))).andExpect(status().isForbidden());
    }

    @Test void concurrentSecurityFailuresHaveUniqueServerCorrelationAndCleanContexts(CapturedOutput output) throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var requests = new ArrayList<java.util.concurrent.Callable<String>>();
            for (int i = 0; i < 12; i++) requests.add(() -> {
                var response = mockMvc.perform(get("/api/auth/me").header("X-Correlation-ID", "attacker-controlled"))
                        .andExpect(status().isUnauthorized()).andReturn().getResponse();
                assertNull(MDC.get("correlationId"));
                assertFalse(AuditRequestContext.hasRecorded());
                return response.getHeader("X-Correlation-ID");
            });
            var ids = new HashSet<String>();
            for (var result : executor.invokeAll(requests)) {
                String id = result.get();
                assertNotEquals("attacker-controlled", id);
                assertTrue(ids.add(id));
                assertTrue(output.getOut().contains("\"correlationId\":\"" + id + "\""));
                assertEquals(1, output.getOut().lines().filter(line -> line.contains("request_completed")
                        && line.contains(id)).count());
            }
            assertEquals(12, ids.size());
            var records = auditRecords.queryPlatform(new com.carwash.audit.domain.AuditQuery(null,
                    com.carwash.audit.domain.AuditAction.BEARER_AUTHENTICATION, null, null, null,
                    java.time.Instant.EPOCH, java.time.Instant.now().plusSeconds(60), null, 100));
            for (String id : ids) assertTrue(records.stream().anyMatch(record -> record.correlationId().equals(id)));
        }
    }

    @Test void actualRenderedJsonDropsSensitiveFrameworkMessagesAndThrowableCauses(CapturedOutput output) throws Exception {
        String secret = "privacy-sentinel-email@example.test-password-JWT-provider-key-vehicle-plate";
        LoggerFactory.getLogger("org.hibernate.engine.jdbc").error("SQL credentials {}", secret,
                new RuntimeException(secret, new RuntimeException("nested-" + secret)));
        LoggerFactory.getLogger("com.carwash.unknown").error("unsafe raw body {}", secret);
        mockMvc.perform(get("/api/unknown/" + secret).queryParam("token", secret)).andExpect(status().isUnauthorized());
        assertFalse(output.getOut().contains(secret));
        assertTrue(output.getOut().contains("\"safe_event\""));
        assertFalse(output.getOut().contains("stack_trace"));
        for (String line : output.getOut().lines().filter(s -> s.startsWith("{")).toList())
            assertNotNull(objectMapper.readTree(line));
    }
}
