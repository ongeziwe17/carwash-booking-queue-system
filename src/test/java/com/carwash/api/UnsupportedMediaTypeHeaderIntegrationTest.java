package com.carwash.api;

import com.carwash.testsupport.ApiContractAssertions;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UnsupportedMediaTypeHeaderIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void unsupportedMediaTypePreservesSupportedMediaTypeGuidance() throws Exception {
        var action = mockMvc.perform(post("/api/services")
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().exists(HttpHeaders.ACCEPT))
                .andExpect(header().string(HttpHeaders.ACCEPT, containsString(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.message").value("Content type is not supported"))
                .andExpect(jsonPath("$.path").value("/api/services"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
        ApiContractAssertions.assertNoSensitiveData(action, false);
    }
}
