package com.carwash.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DataIntegrityIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void duplicateUserIdReturnsStableBusinessRuleErrorWithoutReplacement() throws Exception {
        register("integrity-duplicate-user", "first-integrity@example.com", "Original User");
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"integrity-duplicate-user\","
                                + "\"fullName\":\"Replacement User\","
                                + "\"email\":\"second-integrity@example.com\","
                                + "\"phone\":\"0820000002\","
                                + "\"password\":\"LocalTestPassword123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("User ID already exists"))
                .andExpect(jsonPath("$.path").value("/api/users"))
                .andExpect(jsonPath("$.fieldErrors", empty()));
    }

    private void register(String userId, String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\","
                                + "\"fullName\":\"" + fullName + "\","
                                + "\"email\":\"" + email + "\","
                                + "\"phone\":\"0820000001\","
                                + "\"password\":\"LocalTestPassword123!\"}"))
                .andExpect(status().isCreated());
    }
}
