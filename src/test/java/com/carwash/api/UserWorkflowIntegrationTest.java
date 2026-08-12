package com.carwash.api;

import com.carwash.identity.api.dto.CreateUserRequest;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.UserFixtureBuilder;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserWorkflowIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void userApiCreateAndGetAll() throws Exception {
        CreateUserRequest user = UserFixtureBuilder.valid(ids).build();
        api.createUser(user)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(user.userId()));

        mockMvc.perform(get("/api/users").with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId == '" + user.userId() + "')]").isNotEmpty());
    }
}
