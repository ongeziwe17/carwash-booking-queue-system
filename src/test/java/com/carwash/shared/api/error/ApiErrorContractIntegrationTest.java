package com.carwash.shared.api.error;

import com.carwash.identity.domain.User;

import com.carwash.testsupport.ApiContractAssertions;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(ApiErrorContractIntegrationTest.FailureController.class)
class ApiErrorContractIntegrationTest extends ApiIntegrationTestSupport {

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
        MvcResult result = mockMvc.perform(post("/api/bookings").with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
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
        assertMalformed(post("/api/users").with(anonymous()).contentType(MediaType.APPLICATION_JSON).content("{"));
        assertMalformed(post("/api/users").with(anonymous()).contentType(MediaType.APPLICATION_JSON).content(""));
        assertMalformed(post("/api/services").with(authentication.platformAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":\"bad-service\",\"serviceName\":\"Wash\",\"price\":\"abc\",\"estimatedDurationMin\":30}"));
        assertMalformed(put("/api/admin/users/target/role").with(authentication.platformAdminJwt())
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleName\":\"ADMIN\"}"));
        assertMalformed(post("/api/users").with(anonymous()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"bad-user\",\"fullName\":\"User\",\"email\":\"bad@example.test\",\"phone\":\"1\",\"password\":\"LocalTestPassword123!\",\"passwordHash\":\"secret-value\"}"));
    }

    @Test
    void requestParameterErrorsHaveDistinctCodes() throws Exception {
        mockMvc.perform(get("/api/reports/daily-summary").with(authentication.platformAdminJwt()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
        mockMvc.perform(get("/api/reports/daily-summary").with(authentication.platformAdminJwt()).param("date", "not-a-date"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'date'"));
        mockMvc.perform(get("/api/services").with(authentication.platformAdminJwt()).param("active", "sometimes"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void protocolAndResourceErrorsUseStandardContract() throws Exception {
        var missing = mockMvc.perform(get("/api/services/{id}", ids.service()).with(authentication.platformAdminJwt()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        ApiContractAssertions.assertNoSensitiveData(missing, false);
        mockMvc.perform(get("/api/does-not-exist").with(authentication.platformAdminJwt()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(patch("/api/services").with(authentication.platformAdminJwt()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mockMvc.perform(post("/api/services").with(authentication.platformAdminJwt())
                        .contentType(MediaType.TEXT_PLAIN).content("not-json"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void authenticationAndAuthorizationUseStandardContract() throws Exception {
        var unauthorized = mockMvc.perform(get("/api/users").with(anonymous()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        ApiContractAssertions.assertNoSensitiveData(unauthorized, false);
        var forbidden = mockMvc.perform(get("/api/users").with(authentication.customerJwt(ids.user())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        ApiContractAssertions.assertNoSensitiveData(forbidden, false);
    }

    @Test
    void unexpectedFailuresReturnGenericSafeResponse() throws Exception {
        String internalSecret = "jwt-or-password-secret-value";
        MvcResult result = mockMvc.perform(get("/api/test/unexpected").with(authentication.platformAdminJwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.message", not(containsString(internalSecret))))
                .andReturn();
        assertSafeAndComplete(result, List.of(internalSecret, "RuntimeException", "stackTrace", "Authorization"));
    }

    private void assertMalformed(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed or invalid request body"))
                .andExpect(jsonPath("$.fieldErrors").isArray()).andReturn();
        assertSafeAndComplete(result, List.of("secret-value", "passwordHash", "Exception"));
    }

    private void assertSafeAndComplete(MvcResult result, List<String> forbiddenValues) throws Exception {
        String body = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        assertDoesNotThrow(() -> Instant.parse(json.get("timestamp").asString()));
        assertNotNull(json.get("status"));
        assertNotNull(json.get("code"));
        assertNotNull(json.get("message"));
        assertNotNull(json.get("path"));
        assertTrue(json.get("fieldErrors").isArray());
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
