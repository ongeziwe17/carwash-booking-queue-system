package com.carwash.testsupport;

import com.carwash.identity.api.dto.CreateUserRequest;
import com.carwash.access.api.dto.LoginRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class AuthenticationTestClient {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public AuthenticationTestClient(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public String registerAndLogin(CreateUserRequest user) throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isCreated());
        return login(user.email(), user.password());
    }

    public String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        return response.get("accessToken").asString();
    }

    public RequestPostProcessor platformAdminJwt() {
        return roleJwt("test-platform-admin", "PLATFORM_ADMIN",
                "ROLE_PLATFORM_ADMIN", "PERM_SERVICE_READ", "PERM_SERVICE_MANAGE", "PERM_QUEUE_OPERATE",
                "PERM_MARKETPLACE_READ", "PERM_MARKETPLACE_MANAGE");
    }

    public RequestPostProcessor customerJwt(String userId) {
        return roleJwt(userId, "CUSTOMER", "ROLE_CUSTOMER");
    }

    public RequestPostProcessor roleJwt(String subject, String role, String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new);
        return jwt()
                .jwt(token -> token.subject(subject).claim("role", role))
                .authorities(granted);
    }
}
