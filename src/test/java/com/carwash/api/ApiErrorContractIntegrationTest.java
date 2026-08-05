package com.carwash.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(ApiErrorContractIntegrationTest.FailureController.class)
class ApiErrorContractIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void configureMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .defaultRequest(get("/").with(jwt()
                        .jwt(jwt -> jwt.subject("api-error-admin").claim("role", "PLATFORM_ADMIN"))
                        .authorities(
                                new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                                new SimpleGrantedAuthority("PERM_SERVICE_READ"),
                                new SimpleGrantedAuthority("PERM_SERVICE_MANAGE"),
                                new SimpleGrantedAuthority("PERM_QUEUE_OPERATE"))))
                .apply(springSecurity())
                .build();
    }

    @Test
    void validationErrorsUseStableSortedContract() throws Exception {
        String body = """
                {
                  "bookingId": " ",
                  "userId": " ",
                  "vehicleId": "vehicle",
                  "serviceId": "service",
                  "scheduledDateTime": null
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/bookings"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(3)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("bookingId"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("scheduledDateTime"))
                .andExpect(jsonPath("$.fieldErrors[2].field").value("userId"))
                .andReturn();

        assertSafeAndComplete(result, List.of("stackTrace", "MethodArgumentNotValidException"));
    }

    @Test
    void malformedBodiesAndUnknownPropertiesAreSafe() throws Exception {
        assertMalformed(post("/api/users").with(anonymous())
                .contentType(MediaType.APPLICATION_JSON).content("{"));
        assertMalformed(post("/api/users").with(anonymous())
                .contentType(MediaType.APPLICATION_JSON).content(""));
        assertMalformed(post("/api/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":\"s\",\"serviceName\":\"Wash\",\"price\":\"abc\",\"estimatedDurationMin\":30}"));
        assertMalformed(put("/api/admin/users/target/role")
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleName\":\"ADMIN\"}"));
        assertMalformed(post("/api/users").with(anonymous())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"u\",\"fullName\":\"User\",\"email\":\"u@example.com\",\"phone\":\"1\",\"password\":\"LocalTestPassword123!\",\"passwordHash\":\"secret-value\"}"));
    }

    @Test
    void requestParameterErrorsHaveDistinctCodes() throws Exception {
        mockMvc.perform(get("/api/reports/daily-summary"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.fieldErrors").isArray());

        mockMvc.perform(get("/api/reports/daily-summary").param("date", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'date'"));

        mockMvc.perform(get("/api/services").param("active", "sometimes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void protocolAndResourceErrorsUseStandardContract() throws Exception {
        mockMvc.perform(get("/api/services/missing-" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.fieldErrors").isArray());

        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(patch("/api/services"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void authenticationAndAuthorizationUseStandardContract() throws Exception {
        mockMvc.perform(get("/api/users").with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors").isArray());

        mockMvc.perform(get("/api/users").with(jwt()
                        .jwt(jwt -> jwt.subject("customer").claim("role", "CUSTOMER"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void unexpectedFailuresReturnGenericSafeResponse() throws Exception {
        String internalSecret = "jwt-or-password-secret-value";
        MvcResult result = mockMvc.perform(get("/api/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.message", not(containsString(internalSecret))))
                .andReturn();
        assertSafeAndComplete(result, List.of(internalSecret, "RuntimeException", "stackTrace", "Authorization"));
    }

    private void assertMalformed(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed or invalid request body"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andReturn();
        assertSafeAndComplete(result, List.of("secret-value", "passwordHash", "Json", "Exception"));
    }

    private void assertSafeAndComplete(MvcResult result, List<String> forbiddenValues) throws Exception {
        String body = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        assertDoesNotThrow(() -> Instant.parse(json.get("timestamp").asString()));
        org.junit.jupiter.api.Assertions.assertNotNull(json.get("status"));
        org.junit.jupiter.api.Assertions.assertNotNull(json.get("code"));
        org.junit.jupiter.api.Assertions.assertNotNull(json.get("message"));
        org.junit.jupiter.api.Assertions.assertNotNull(json.get("path"));
        org.junit.jupiter.api.Assertions.assertTrue(json.get("fieldErrors").isArray());
        forbiddenValues.forEach(value -> assertFalse(body.contains(value), () -> "Response exposed: " + value));
    }

    @RestController
    static class FailureController {
        @GetMapping("/api/test/unexpected")
        Object fail() {
            throw new RuntimeException("jwt-or-password-secret-value");
        }
    }
}
