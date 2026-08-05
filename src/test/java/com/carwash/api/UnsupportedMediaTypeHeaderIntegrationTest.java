package com.carwash.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UnsupportedMediaTypeHeaderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unsupportedMediaTypePreservesSupportedMediaTypeGuidance() throws Exception {
        mockMvc.perform(post("/api/services")
                        .with(jwt()
                                .jwt(token -> token.subject("media-type-admin")
                                        .claim("role", "PLATFORM_ADMIN"))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                                        new SimpleGrantedAuthority("PERM_SERVICE_MANAGE")))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().exists(HttpHeaders.ACCEPT))
                .andExpect(header().string(HttpHeaders.ACCEPT,
                        containsString(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.message").value("Content type is not supported"))
                .andExpect(jsonPath("$.path").value("/api/services"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }
}
