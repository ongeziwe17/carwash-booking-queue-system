package com.carwash.api;

import com.carwash.api.dto.CreateUserRequest;
import com.carwash.domain.User;
import com.carwash.enums.AccountStatus;
import com.carwash.repository.UserRepository;
import com.carwash.testsupport.ApiContractAssertions;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.UserFixtureBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    UserRepository users;

    @Test
    void loginNormalizesEmailReturnsSafeBearerTokenAndPersistsLogin() throws Exception {
        CreateUserRequest user = UserFixtureBuilder.valid(ids).build();
        api.createUser(user).andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.carwash.api.dto.LoginRequest(
                                " " + user.email().toUpperCase() + " ", user.password()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.expiresInSeconds").value(1200))
                .andExpect(jsonPath("$.user.userId").value(user.userId()))
                .andExpect(content().string(not(containsString("encodedPassword"))))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        String token = json.get("accessToken").asString();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.userId()))
                .andExpect(jsonPath("$.roleName").value("CUSTOMER"));
        assertNotNull(users.findById(user.userId()).orElseThrow().getLastLoginAt());
    }

    @Test
    void invalidLoginsUseIdenticalSafeUnauthorizedResponse() throws Exception {
        CreateUserRequest user = UserFixtureBuilder.valid(ids).build();
        api.createUser(user).andExpect(status().isCreated());
        assertInvalid(ids.emailFor(ids.user()), "WrongPassword123!");
        assertInvalid(user.email(), "WrongPassword123!");
        User stored = users.findById(user.userId()).orElseThrow();
        stored.setAccountStatus(AccountStatus.SUSPENDED);
        assertTrue(users.update(stored));
        assertInvalid(user.email(), user.password());
    }

    @Test
    void validationAndBearerProtectionUseJsonErrors() throws Exception {
        var validation = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad\",\"password\":\" \"}"))
                .andExpect(status().isBadRequest());
        ApiContractAssertions.assertNoSensitiveData(validation, false);

        var missing = mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));
        ApiContractAssertions.assertNoSensitiveData(missing, false);

        var malformed = mockMvc.perform(get("/api/users").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        ApiContractAssertions.assertNoSensitiveData(malformed, false);
    }

    @Test
    void registrationAndDocumentationRemainPublic() throws Exception {
        api.createUser(UserFixtureBuilder.valid(ids).build()).andExpect(status().isCreated());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    private void assertInvalid(String email, String password) throws Exception {
        var action = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.carwash.api.dto.LoginRequest(email, password))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
        ApiContractAssertions.assertNoSensitiveData(action, false);
    }
}
