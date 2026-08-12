package com.carwash.identity.api;

import com.carwash.identity.domain.User;

import com.carwash.identity.api.dto.CreateUserRequest;
import com.carwash.testsupport.ApiContractAssertions;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.UserFixtureBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserApiContractIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void registrationAndReadsExposeOnlySafeResponseFields() throws Exception {
        CreateUserRequest user = UserFixtureBuilder.valid(ids)
                .fullName(" Safe User ").email(" Safe.User@Example.COM ").phone(" 123 ").build();
        var registration = api.createUser(user)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(user.userId()))
                .andExpect(jsonPath("$.fullName").value("Safe User"))
                .andExpect(jsonPath("$.email").value("safe.user@example.com"))
                .andExpect(jsonPath("$.phone").value("123"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
        assertSafe(registration);
        assertSafe(mockMvc.perform(get("/api/users/{id}", user.userId()).with(authentication.platformAdminJwt()))
                .andExpect(status().isOk()));
        assertSafe(mockMvc.perform(get("/api/users").with(authentication.platformAdminJwt())).andExpect(status().isOk()));
    }

    @Test
    void updateRejectsUnknownServerControlledFields() throws Exception {
        CreateUserRequest user = UserFixtureBuilder.valid(ids).build();
        api.createUser(user).andExpect(status().isCreated());
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "Updated User");
        body.put("email", ids.emailFor(ids.user()));
        body.put("phone", "222");
        body.put("passwordHash", "must-not-be-returned");
        String response = mockMvc.perform(put("/api/users/{id}", user.userId())
                        .with(authentication.platformAdminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("must-not-be-returned"));
    }

    @Test
    void invalidRegistrationFieldsReturnStandardBadRequest() throws Exception {
        CreateUserRequest request = UserFixtureBuilder.valid(ids).build();
        Map<String, Object> valid = new HashMap<>(Map.of(
                "userId", request.userId(), "fullName", request.fullName(), "email", request.email(),
                "phone", request.phone(), "password", request.password()));
        for (String field : new String[]{"userId", "fullName", "email", "phone", "password"}) {
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
        CreateUserRequest request = UserFixtureBuilder.valid(ids).build();
        String shortSecret = "tiny-secret";
        Map<String, Object> tooShort = new HashMap<>(Map.of(
                "userId", request.userId(), "fullName", request.fullName(), "email", request.email(),
                "phone", request.phone(), "password", shortSecret));
        String response = mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tooShort)))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains(shortSecret));

        Map<String, Object> tooLong = new HashMap<>(tooShort);
        tooLong.put("userId", ids.user());
        tooLong.put("email", ids.emailFor(ids.user()));
        tooLong.put("password", "x".repeat(201));
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tooLong)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateEmailsAreRejectedRegardlessOfCaseOrWhitespace() throws Exception {
        String email = ids.emailFor(ids.user());
        createWithEmail(email).andExpect(status().isCreated());
        createWithEmail(email).andExpect(status().isBadRequest());
        createWithEmail(email.toUpperCase()).andExpect(status().isBadRequest());
        createWithEmail(" " + email + " ").andExpect(status().isBadRequest());
    }

    @Test
    void updateToAnotherUsersEmailIsRejected() throws Exception {
        CreateUserRequest first = UserFixtureBuilder.valid(ids).build();
        CreateUserRequest second = UserFixtureBuilder.valid(ids).build();
        api.createUser(first).andExpect(status().isCreated());
        api.createUser(second).andExpect(status().isCreated());
        mockMvc.perform(put("/api/users/{id}", second.userId())
                        .with(authentication.platformAdminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Second", "email", " " + first.email().toUpperCase() + " ", "phone", "222"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private org.springframework.test.web.servlet.ResultActions createWithEmail(String email) throws Exception {
        return api.createUser(UserFixtureBuilder.valid(ids).email(email).build());
    }

    private void assertBadRequest(Map<String, Object> body) throws Exception {
        var action = mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.path").value("/api/users"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
        ApiContractAssertions.assertNoSensitiveData(action, false);
    }

    private void assertSafe(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        result.andExpect(jsonPath("$..password").doesNotExist())
                .andExpect(jsonPath("$..passwordHash").doesNotExist())
                .andExpect(jsonPath("$..encodedPassword").doesNotExist());
        ApiContractAssertions.assertNoSensitiveData(result, false);
    }
}
