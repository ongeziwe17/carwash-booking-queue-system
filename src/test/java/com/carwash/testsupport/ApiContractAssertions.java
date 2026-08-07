package com.carwash.testsupport;

import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

public final class ApiContractAssertions {

    private static final List<String> FORBIDDEN_RESPONSE_FRAGMENTS = List.of(
            "encodedPassword",
            "passwordHash",
            "SECURE_JWT_SECRET",
            "carwash.security.jwt.secret",
            "dGVzdC1vbmx5LWNhcndhc2gtand0LXNpZ25pbmcta2V5LTMyYnl0ZXMh",
            "LocalTestPassword123!",
            "Authorization",
            "stackTrace",
            "java.lang.",
            "org.springframework."
    );

    private ApiContractAssertions() {
    }

    public static ResultActions assertStandardError(
            ResultActions result,
            int status,
            String code,
            String path
    ) throws Exception {
        result.andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.fieldErrors").isArray());
        String timestamp = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(result.andReturn().getResponse().getContentAsString())
                .get("timestamp").asString();
        assertDoesNotThrow(() -> Instant.parse(timestamp));
        return assertNoSensitiveData(result, false);
    }

    public static ResultActions assertNoSensitiveData(ResultActions result, boolean allowAccessToken) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        for (String forbidden : FORBIDDEN_RESPONSE_FRAGMENTS) {
            assertFalse(body.contains(forbidden), () -> "Response exposed forbidden fragment: " + forbidden);
        }
        if (!allowAccessToken) {
            assertFalse(body.contains("accessToken"), "Access tokens belong only in authentication responses");
        }
        return result;
    }
}
