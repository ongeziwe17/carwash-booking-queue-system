package com.carwash.api;

import com.carwash.domain.User;
import com.carwash.enums.AccountStatus;
import com.carwash.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;

    @Test
    void loginNormalizesEmailReturnsSafeBearerTokenAndPersistsLogin() throws Exception {
        register("auth-success", "customer@example.com");
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" CUSTOMER@EXAMPLE.COM \",\"password\":\"LocalTestPassword123!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty()).andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.expiresInSeconds").value(1200)).andExpect(jsonPath("$.user.userId").value("auth-success"))
                .andExpect(content().string(not(containsString("encodedPassword"))))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String token = json.get("accessToken").asString();
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertNotNull(users.findById("auth-success").orElseThrow().getLastLoginAt());
    }

    @Test
    void invalidLoginsUseIdenticalSafeUnauthorizedResponse() throws Exception {
        register("auth-invalid", "valid@example.com");
        assertInvalid("missing@example.com", "WrongPassword123!");
        assertInvalid("valid@example.com", "WrongPassword123!");
        User user = users.findById("auth-invalid").orElseThrow();
        user.setAccountStatus(AccountStatus.SUSPENDED);
        users.save(user);
        assertInvalid("valid@example.com", "LocalTestPassword123!");
    }

    @Test
    void validationAndBearerProtectionUseJsonErrors() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad\",\"password\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("secret-value"))));
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void registrationAndDocumentationRemainPublic() throws Exception {
        register("public-user", "public@example.com");
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    private void register(String id, String email) throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + id
                        + "\",\"fullName\":\"Test User\",\"email\":\"" + email
                        + "\",\"phone\":\"0821234567\",\"password\":\"LocalTestPassword123!\"}"))
                .andExpect(status().isCreated());
    }

    private void assertInvalid(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }
}
