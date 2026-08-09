package com.carwash.api;

import com.carwash.repository.BookingRepository;
import com.carwash.repository.NotificationRepository;
import com.carwash.repository.QueueEntryRepository;
import com.carwash.repository.ServiceRepository;
import com.carwash.repository.UserRepository;
import com.carwash.repository.VehicleRepository;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiQualityGateIntegrationTest extends ApiIntegrationTestSupport {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "delete", "patch");
    private static final Pattern PATH_PLACEHOLDER = Pattern.compile("\\{([^}/]+)}");
    private static final Set<String> EXPECTED_OPERATIONS = Set.of(
            "GET /api/vehicles/{id}", "PUT /api/vehicles/{id}", "DELETE /api/vehicles/{id}",
            "GET /api/users/{id}", "PUT /api/users/{id}", "DELETE /api/users/{id}",
            "GET /api/services/{id}", "PUT /api/services/{id}", "DELETE /api/services/{id}",
            "PUT /api/queue-entries/{id}/position", "GET /api/bookings/{id}",
            "PUT /api/bookings/{id}", "DELETE /api/bookings/{id}",
            "PUT /api/admin/users/{userId}/role", "GET /api/vehicles", "POST /api/vehicles",
            "GET /api/users", "POST /api/users", "GET /api/services", "POST /api/services",
            "POST /api/services/{id}/deactivate", "POST /api/services/{id}/activate",
            "GET /api/queue-entries", "POST /api/queue-entries", "POST /api/queue-entries/{id}/start",
            "POST /api/queue-entries/{id}/complete", "POST /api/queue-entries/{id}/call-next",
            "GET /api/bookings", "POST /api/bookings", "POST /api/bookings/{id}/confirm",
            "POST /api/bookings/{id}/cancel", "POST /api/auth/login", "GET /api/reports/daily-summary",
            "GET /api/queue-entries/{id}", "DELETE /api/queue-entries/{id}",
            "GET /api/notifications/user/{userId}", "GET /api/auth/me"
    );

    @Autowired UserRepository users;
    @Autowired VehicleRepository vehicles;
    @Autowired ServiceRepository services;
    @Autowired BookingRepository bookings;
    @Autowired QueueEntryRepository queues;
    @Autowired NotificationRepository notifications;

    @Test
    void openApiDocsEndpointAvailableWithoutBusinessData() throws Exception {
        assertRepositoriesEmpty();
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        assertRepositoriesEmpty();
    }

    @Test
    void everyJsonRequestBodyReferencesNamedDtoSchema() throws Exception {
        JsonNode document = openApi();
        forEachOperation(document, (method, path, operation) -> {
            JsonNode content = operation.path("requestBody").path("content");
            if (content.has("application/json")) {
                String ref = content.path("application/json").path("schema").path("$ref").asText();
                assertTrue(ref.startsWith("#/components/schemas/"), method + " " + path + " uses inline request schema");
                String schema = ref.substring(ref.lastIndexOf('/') + 1);
                assertTrue(schema.endsWith("Request"), method + " " + path + " must reference a request DTO");
            }
        });
    }

    @Test
    void everyDocumentedErrorResponseReferencesStandardApiError() throws Exception {
        JsonNode document = openApi();
        forEachOperation(document, (method, path, operation) -> operation.path("responses").properties()
                .forEach(entry -> {
                    String code = entry.getKey();
                    if (code.startsWith("4") || code.startsWith("5")) {
                        String ref = responseSchemaRef(entry.getValue());
                        assertEquals("#/components/schemas/ApiErrorResponse", ref,
                                method + " " + path + " response " + code);
                    }
                }));
    }

    @Test
    void schemasKeepCredentialsAndInternalAggregateGraphsBounded() throws Exception {
        JsonNode schemas = openApi().path("components").path("schemas");
        JsonNode user = schemas.path("User").path("properties");
        for (String forbidden : Set.of("password", "encodedPassword", "passwordHash", "vehicles", "bookings", "notifications")) {
            assertFalse(user.has(forbidden), "User schema exposed internal field " + forbidden);
        }
        assertFalse(schemas.path("Booking").path("properties").has("queueEntry"));
        assertFalse(schemas.path("Vehicle").path("properties").has("user"));
        assertTrue(schemas.path("CreateUserRequest").path("properties").path("password").path("writeOnly").asBoolean());
        assertTrue(schemas.path("LoginRequest").path("properties").path("password").path("writeOnly").asBoolean());
    }

    @Test
    void queueCreationSchemaKeepsPositionServerManaged() throws Exception {
        JsonNode document = openApi();
        JsonNode queueRequest = document.path("components").path("schemas").path("CreateQueueEntryRequest");
        Set<String> required = new HashSet<>();
        queueRequest.path("required").forEach(field -> required.add(field.asText()));

        assertEquals(Set.of("queueEntryId", "bookingId", "serviceId"), required);
        assertFalse(queueRequest.path("properties").has("position"));
        assertTrue(document.path("paths").path("/api/queue-entries").path("post")
                .path("description").asText().contains("server assigns"));
    }

    @Test
    void authenticationRequirementsMatchPublicAndProtectedEndpoints() throws Exception {
        JsonNode document = openApi();
        assertTrue(document.path("security").isArray() && !document.path("security").isEmpty());
        assertTrue(document.path("paths").path("/api/auth/login").path("post").path("security").isEmpty());
        assertTrue(document.path("paths").path("/api/users").path("post").path("security").isEmpty());

        forEachOperation(document, (method, path, operation) -> {
            boolean publicOperation = (path.equals("/api/auth/login") && method.equals("POST"))
                    || (path.equals("/api/users") && method.equals("POST"));
            if (!publicOperation) {
                JsonNode effective = operation.has("security") ? operation.path("security") : document.path("security");
                assertTrue(effective.isArray() && !effective.isEmpty(), method + " " + path + " must require authentication");
            }
        });
    }

    @Test
    void allControllerOperationsAreRepresented() throws Exception {
        Set<String> actual = new HashSet<>();
        forEachOperation(openApi(), (method, path, operation) -> actual.add(method + " " + path));
        assertEquals(EXPECTED_OPERATIONS, actual);
    }

    @Test
    void operationsHaveUniqueIdsSummariesAndSuccessfulResponses() throws Exception {
        Set<String> operationIds = new HashSet<>();
        forEachOperation(openApi(), (method, path, operation) -> {
            String label = method + " " + path;
            String operationId = operation.path("operationId").asText();
            assertFalse(operationId.isBlank(), label + " must have an operationId");
            assertTrue(operationIds.add(operationId), "Duplicate operationId: " + operationId);
            assertFalse(operation.path("summary").asText().isBlank(), label + " must have a summary");

            boolean hasSuccess = operation.path("responses").properties().stream()
                    .anyMatch(entry -> entry.getKey().startsWith("2"));
            assertTrue(hasSuccess, label + " must document a 2xx response");
        });
    }

    @Test
    void pathParametersAndLocalReferencesAreImportable() throws Exception {
        JsonNode document = openApi();
        assertLocalComponentReferencesResolve(document, document, "#");

        forEachOperation(document, (method, path, operation) -> {
            Matcher placeholders = PATH_PLACEHOLDER.matcher(path);
            while (placeholders.find()) {
                String expectedName = placeholders.group(1);
                JsonNode parameter = findPathParameter(operation.path("parameters"), expectedName);
                assertFalse(parameter.isMissingNode(), method + " " + path
                        + " is missing path parameter " + expectedName);
                assertTrue(parameter.path("required").asBoolean(), method + " " + path
                        + " path parameter " + expectedName + " must be required");
            }
        });
    }

    private JsonNode openApi() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String responseSchemaRef(JsonNode response) {
        JsonNode content = response.path("content");
        JsonNode json = content.path("application/json");
        if (!json.isMissingNode()) {
            return json.path("schema").path("$ref").asText();
        }
        Iterator<Map.Entry<String, JsonNode>> mediaTypes = content.properties().iterator();
        return mediaTypes.hasNext()
                ? mediaTypes.next().getValue().path("schema").path("$ref").asText()
                : "";
    }

    private JsonNode findPathParameter(JsonNode parameters, String name) {
        if (parameters.isArray()) {
            for (JsonNode parameter : parameters) {
                if ("path".equals(parameter.path("in").asText()) && name.equals(parameter.path("name").asText())) {
                    return parameter;
                }
            }
        }
        return parameters.path("__missing_path_parameter__");
    }

    private void assertLocalComponentReferencesResolve(JsonNode document, JsonNode node, String location) {
        if (node.isObject()) {
            String ref = node.path("$ref").asText();
            if (!ref.isBlank()) {
                assertTrue(ref.startsWith("#/components/"), location + " uses a non-local component reference: " + ref);
                assertFalse(document.at(ref.substring(1)).isMissingNode(), location + " has unresolved reference " + ref);
            }
            node.properties().forEach(entry ->
                    assertLocalComponentReferencesResolve(document, entry.getValue(), location + "/" + entry.getKey()));
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                assertLocalComponentReferencesResolve(document, node.get(index), location + "/" + index);
            }
        }
    }

    private void forEachOperation(JsonNode document, OperationConsumer consumer) {
        document.path("paths").properties().forEach(pathEntry -> {
            String path = pathEntry.getKey();
            pathEntry.getValue().properties().forEach(methodEntry -> {
                String method = methodEntry.getKey().toLowerCase();
                if (HTTP_METHODS.contains(method)) {
                    consumer.accept(method.toUpperCase(), path, methodEntry.getValue());
                }
            });
        });
    }

    private void assertRepositoriesEmpty() {
        assertTrue(users.findAll().isEmpty());
        assertTrue(vehicles.findAll().isEmpty());
        assertTrue(services.findAll().isEmpty());
        assertTrue(bookings.findAll().isEmpty());
        assertTrue(queues.findAll().isEmpty());
        assertTrue(notifications.findAll().isEmpty());
    }

    @FunctionalInterface
    private interface OperationConsumer {
        void accept(String method, String path, JsonNode operation);
    }
}
