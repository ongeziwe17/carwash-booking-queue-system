package com.carwash.testsupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestNotificationIdConfiguration.class, InMemoryTestDataCleaner.class})
public abstract class ApiIntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected InMemoryTestDataCleaner testDataCleaner;

    protected TestIdFactory ids;
    protected ApiTestClient api;
    protected AuthenticationTestClient authentication;

    @BeforeEach
    final void resetApplicationData(TestInfo testInfo) {
        testDataCleaner.clean();
        String className = testInfo.getTestClass().map(Class::getSimpleName).orElse("integration");
        String methodName = testInfo.getTestMethod().map(method -> method.getName()).orElse("test");
        ids = new TestIdFactory(className + "-" + methodName);
        authentication = new AuthenticationTestClient(mockMvc, objectMapper);
        api = new ApiTestClient(mockMvc, objectMapper, authentication);
    }
}
