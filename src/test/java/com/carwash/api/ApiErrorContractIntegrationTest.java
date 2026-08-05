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
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void openApiDocumentsActualRequestAndErrorContracts() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs").with(anonymous()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode openApi = objectMapper.readTree(result.getResponse().getContentAsString());

        assertOperation(openApi, "/api/admin/users/{userId}/role", "put",
                "200", "400", "401", "403", "404", "405", "415", "500");
        assertOperation(openApi, "/api/auth/login", "post",
                "200", "400", "401", "405", "415", "500");
        assertOperation(openApi, "/api/auth/me", "get",
                "200", "401", "404", "405", "500");

        assertOperation(openApi, "/api/bookings", "get",
                "200", "401", "403", "405", "500");
        assertOperation(openApi, "/api/bookings", "post",
                "201", "400", "401", "403", "404", "405", "415", "500");
        assertOperation(openApi, "/api/bookings/{id}", "get",
                "200", "400", "401", "403", "404", "405", "500");
        assertOperation(openApi, "/api/bookings/{id}", "put",
                "200", "400", "401", "403", "404", "405", "415", "500");
        assertOperation(openApi, "/api/bookings/{id}", "delete",
                "204", "400", "401", "403", "404", "405", "500");
        assertOperation(openApi, "/api/bookings/{id}/confirm", "post",
                "200", "400", "401", "403", "404", "405", "500");
        assertOperation(openApi, "/api/bookings/{id}/cancel", "post",
                "200", "400", "401", "403", "404", "405", "500");

        assertOperation(openApi, "/api/notifications/user/{userId}", "get",
                "200", "400", "401", "403", "405", "500");

        assertOperation(openApi, "/api/queue-entries", "get",
                "200", "401", "403", "405", "500");
        assertOperation(openApi, "/api/queue-entries", "post",
                "201", "400", "401", "403", "404", "405", "415", "500");
        assertOperation(openApi, "/api/queue-entries/{id}", "get",
                "200", "400", "401", "403", "404", "405", "500");
        assertOperation(openApi, "/api/queue-entries/{id}", "delete",
                "204", "400", "401", "403", "404", "405", "500");
        assertOperation(openApi, "/api/queue-entries/{id}/position", "put",
                "200", "400", "401", "403", "404", "405", "415", "500");
        assertOperation(openApi, "/api/queue-entries/{id}/call-next", "post",
                "200", "400", "401", "403", "404", "405", "500");
        assertOperation(openApi, "/api/queue-entries/{id}/start", "post",
                "200", "400", "401", "403", "404", "405", "500");
        assertOperation(openApi, "/api/queue-entries/{id}/complete", "post",
                "200", "400", "401", "403", "404", "405", "500");

        assertOperation(openApi, "/api/reports/daily-summary", "get",
                "200", "400", "401", "403", "405", "500");

        assertOperation(openApi, "/api/services", "get",
                "200", "400", "401", "403", "405", "500");
        assertOperation(openApi, "/api/services", "post",
                "201", "400", "401", "403", "405", "415", "500");
        assertOperation(openApi, "/api/services/{id}", "get",
                "200", "400", "401", "403", "404", "405", "500");
        assertOperation(openApi, "/api/services/{id}", "put",
                "200", "400", "401", "403", "404", "405", "415", "500");
        assertOperation(openApi, "/api/services/{id}", "delete",
                "204", "400", "401", "403", "404", "405", "500");
        assertOperation(openApi, "/api/services/{id}/activate", "post",
                "200", "400", "401", "403", "404", "405", "500");
        assertOperation(openApi, "/api/services/{id}/deactivate", "post",
                "200", "400", "401", "403", "404", "405", "500");

        assertOperation(openApi, "/api/users", "get",
                "200", "401", "403", "405", "500");
        assertOperation(openApi, "/api/users", "post",
                "201", "400", "405", "415", "500");
        assertOperation(openApi, "/api/users/{id}", "get",
                "200", "400", "401", "403", "404", "405", "500");
        assertOperation(openApi, "/api/users/{id}", "put",
                "200", "400", "401", "403", "404", "405", "415", "500");
        assertOperation(openApi, "/api/users/{id}", "delete",
                "204", "400", "401", "403", "404", "405", "500");

        assertOperation(openApi, "/api/vehicles", "get",
                "200", "401", "403", "405", "500");
        assertOperation(openApi, "/api/vehicles", "post",
                "201", "400", "401", "403", "404", "405", "415", "500");
        assertOperation(openApi, "/api/vehicles/{id}", "get",
                "200", "400", "401", "403", "404", "405", "500");
        assertOperation(openApi, "/api/vehicles/{id}", "put",
                "200", "400", "401", "403", "404", "405", "415", "500");
        assertOperation(openApi, "/api/vehicles/{id}", "delete",
                "204", "400", "401", "403", "404", "405", "500");

        assertRequestSchema(openApi, "/api/admin/users/{userId}/role", "put", "AssignRoleRequest");
        assertRequestSchema(openApi, "/api/auth/login", "post", "LoginRequest");
        assertRequestSchema(openApi, "/api/bookings", "post", "CreateBookingRequest");
        assertRequestSchema(openApi, "/api/bookings/{id}", "put", "UpdateBookingRequest");
        assertRequestSchema(openApi, "/api/queue-entries", "post", "CreateQueueEntryRequest");
        assertRequestSchema(openApi, "/api/queue-entries/{id}/position", "put",
                "UpdateQueuePositionRequest");
        assertRequestSchema(openApi, "/api/services", "post", "CreateServiceRequest");
        assertRequestSchema(openApi, "/api/services/{id}", "put", "UpdateServiceRequest");
        assertRequestSchema(openApi, "/api/users", "post", "CreateUserRequest");
        assertRequestSchema(openApi, "/api/users/{id}", "put", "UpdateUserRequest");
        assertRequestSchema(openApi, "/api/vehicles", "post", "CreateVehicleRequest");
        assertRequestSchema(openApi, "/api/vehicles/{id}", "put", "UpdateVehicleRequest");
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

    private void assertOperation(
            JsonNode openApi,
            String path,
            String method,
            String... expectedResponseCodes
    ) {
        JsonNode operation = openApi.path("paths").path(path).path(method);
        assertFalse(operation.isMissingNode(), () -> "Missing OpenAPI operation: " + method + " " + path);

        JsonNode responses = operation.path("responses");
        assertEquals(
                new TreeSet<>(Set.of(expectedResponseCodes)),
                new TreeSet<>(responses.propertyNames()),
                () -> "Unexpected OpenAPI responses for " + method + " " + path
        );

        for (String responseCode : expectedResponseCodes) {
            if (!responseCode.startsWith("4") && !responseCode.startsWith("5")) {
                continue;
            }
            JsonNode response = responses.path(responseCode);
            boolean usesStandardError = response.path("content").properties().stream()
                    .map(entry -> entry.getValue().path("schema").path("$ref").asString())
                    .anyMatch("#/components/schemas/ApiErrorResponse"::equals);
            assertTrue(usesStandardError,
                    () -> "Response " + responseCode + " must use ApiErrorResponse for " + method + " " + path);
        }
    }

    private void assertRequestSchema(JsonNode openApi, String path, String method, String schemaName) {
        JsonNode requestBody = openApi.path("paths").path(path).path(method).path("requestBody");
        assertFalse(requestBody.isMissingNode(), () -> "Missing request body for " + method + " " + path);
        boolean usesExpectedSchema = requestBody.path("content").properties().stream()
                .map(entry -> entry.getValue().path("schema").path("$ref").asString())
                .anyMatch(("#/components/schemas/" + schemaName)::equals);
        assertTrue(usesExpectedSchema,
                () -> "Expected request schema " + schemaName + " for " + method + " " + path);
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
