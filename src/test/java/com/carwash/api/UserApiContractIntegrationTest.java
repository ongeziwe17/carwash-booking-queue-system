package com.carwash.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class UserApiContractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void registrationAndReadsExposeOnlySafeResponseFields() throws Exception {
        String id = unique("safe");
        var registration = create(id, " Safe User ", " Safe.User@Example.COM ", " 123 ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(id))
                .andExpect(jsonPath("$.fullName").value("Safe User"))
                .andExpect(jsonPath("$.email").value("safe.user@example.com"))
                .andExpect(jsonPath("$.phone").value("123"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        assertSafe(registration);
        assertSafe(mockMvc.perform(get("/api/users/{id}", id)).andExpect(status().isOk()));
        assertSafe(mockMvc.perform(get("/api/users")).andExpect(status().isOk()), "$[?(@.userId == '" + id + "')][0]");
    }

    @Test
    void updateUsesPathIdAndCannotChangeServerControlledFields() throws Exception {
        String id = unique("update");
        create(id, "Original", id + "@example.com", "111").andExpect(status().isCreated());
        String createdAt = objectMapper.readTree(mockMvc.perform(get("/api/users/{id}", id)).andReturn()
                .getResponse().getContentAsString()).get("createdAt").asString();

        Map<String, Object> body = new HashMap<>();
        body.put("userId", "body-id");
        body.put("fullName", " Updated User ");
        body.put("email", " UPDATED." + id + "@Example.COM ");
        body.put("phone", " 222 ");
        body.put("password", "replacement");
        body.put("passwordHash", "replacement-hash");
        body.put("accountStatus", "SUSPENDED");
        body.put("createdAt", "2000-01-01T00:00:00");
        body.put("role", Map.of("roleName", "ADMIN"));

        var result = mockMvc.perform(put("/api/users/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(id))
                .andExpect(jsonPath("$.fullName").value("Updated User"))
                .andExpect(jsonPath("$.email").value(("updated." + id + "@example.com").toLowerCase()))
                .andExpect(jsonPath("$.phone").value("222"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").value(createdAt));
        assertSafe(result);
        mockMvc.perform(get("/api/users/body-id")).andExpect(status().isNotFound());
    }

    @Test
    void invalidRegistrationFieldsReturnStandardBadRequest() throws Exception {
        Map<String, Object> valid = new HashMap<>(request(unique("validation"), "Valid User", "valid." + UUID.randomUUID() + "@example.com", "123"));
        String[] fields = {"userId", "fullName", "email", "phone", "password"};
        for (String field : fields) {
            Map<String, Object> missing = new HashMap<>(valid);
            missing.remove(field);
            assertBadRequest(missing);
            Map<String, Object> blank = new HashMap<>(valid);
            blank.put(field, " ");
            assertBadRequest(blank);
        }
        Map<String, Object> invalidEmail = new HashMap<>(valid);
        invalidEmail.put("email", "not-an-email");
        assertBadRequest(invalidEmail);
    }

    @Test
    void passwordPolicyViolationsAreSafeBadRequests() throws Exception {
        String secret = "tiny-secret";
        Map<String, Object> tooShort = new HashMap<>(request(unique("short"), "Short Password",
                unique("short-email") + "@example.com", "123"));
        tooShort.put("password", secret);
        String response = mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tooShort)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(response.contains(secret));

        Map<String, Object> tooLong = new HashMap<>(tooShort);
        tooLong.put("userId", unique("long"));
        tooLong.put("password", "x".repeat(201));
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tooLong)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateEmailsAreRejectedRegardlessOfCaseOrWhitespace() throws Exception {
        String email = unique("duplicate") + "@example.com";
        create(unique("first"), "First", email, "111").andExpect(status().isCreated());
        create(unique("exact"), "Exact", email, "222").andExpect(status().isBadRequest());
        create(unique("case"), "Case", email.toUpperCase(), "333").andExpect(status().isBadRequest());
        create(unique("space"), "Space", " " + email + " ", "444").andExpect(status().isBadRequest());
    }

    @Test
    void updateToAnotherUsersEmailIsRejected() throws Exception {
        String first = unique("first-update");
        String second = unique("second-update");
        create(first, "First", first + "@example.com", "111").andExpect(status().isCreated());
        create(second, "Second", second + "@example.com", "222").andExpect(status().isCreated());
        mockMvc.perform(put("/api/users/{id}", second).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Second", "email", " " + first.toUpperCase() + "@EXAMPLE.COM ", "phone", "222"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private org.springframework.test.web.servlet.ResultActions create(String id, String name, String email, String phone) throws Exception {
        return mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request(id, name, email, phone))));
    }

    private Map<String, Object> request(String id, String name, String email, String phone) {
        return Map.of("userId", id, "fullName", name, "email", email, "phone", phone,
                "password", "LocalTestPassword123!");
    }

    private void assertBadRequest(Map<String, Object> body) throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.path").value("/api/users"));
    }

    private void assertSafe(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        assertSafe(result, "$");
    }

    private void assertSafe(org.springframework.test.web.servlet.ResultActions result, String root) throws Exception {
        result.andExpect(jsonPath(root + ".password").doesNotExist())
                .andExpect(jsonPath(root + ".passwordHash").doesNotExist())
                .andExpect(jsonPath(root + ".encodedPassword").doesNotExist())
                .andExpect(jsonPath(root + ".credentials").doesNotExist())
                .andExpect(jsonPath(root + ".vehicles").doesNotExist())
                .andExpect(jsonPath(root + ".bookings").doesNotExist())
                .andExpect(jsonPath(root + ".notifications").doesNotExist())
                .andExpect(jsonPath(root + ".role").doesNotExist())
                .andExpect(jsonPath(root + ".permissions").doesNotExist());
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
