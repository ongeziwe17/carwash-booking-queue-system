package com.carwash.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void userApiCreateAndGetAll() throws Exception {
        Map<String, Object> user = Map.of("userId", "u1", "fullName", "Test User", "email", "u1@test.com", "phone", "123", "passwordHash", "x");
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(user))).andExpect(status().isCreated());
        mockMvc.perform(get("/api/users")).andExpect(status().isOk());
    }

    @Test
    void serviceApiCreateAndDeactivate() throws Exception {
        Map<String, Object> service = new HashMap<>();
        service.put("serviceId", "s1"); service.put("serviceName", "Basic"); service.put("description", "basic wash");
        service.put("price", BigDecimal.valueOf(100)); service.put("estimatedDurationMin", 30);
        mockMvc.perform(post("/api/services").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(service))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/services/s1/deactivate")).andExpect(status().isOk());
    }

    @Test
    void bookingApiCreateConfirmAndInvalid() throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"u2\",\"fullName\":\"U 2\",\"email\":\"u2@test.com\",\"phone\":\"123\",\"passwordHash\":\"x\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"u2\",\"vehicleId\":\"v2\",\"plateNumber\":\"ABC123\",\"vehicleType\":\"SUV\",\"brand\":\"Toyota\",\"model\":\"Rav4\",\"color\":\"Black\",\"notes\":\"\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/services").contentType(MediaType.APPLICATION_JSON).content("{\"serviceId\":\"s2\",\"serviceName\":\"Premium\",\"description\":\"premium\",\"price\":300,\"estimatedDurationMin\":45}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/services/s2/activate")).andExpect(status().isOk());

        String validBooking = "{\"bookingId\":\"b1\",\"userId\":\"u2\",\"vehicleId\":\"v2\",\"serviceId\":\"s2\",\"scheduledDateTime\":\"" + LocalDateTime.now().plusDays(1) + "\",\"specialRequest\":\"none\"}";
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(validBooking)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/bookings/b1/confirm")).andExpect(status().isOk());

        String invalidBooking = "{\"bookingId\":\"b2\",\"userId\":\"u2\",\"vehicleId\":\"v2\",\"serviceId\":\"s2\",\"scheduledDateTime\":\"" + LocalDateTime.now().minusDays(1) + "\",\"specialRequest\":\"none\"}";
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(invalidBooking)).andExpect(status().isBadRequest());
    }

    @Test
    void bookingApiCancelOwnFutureBookingSucceeds() throws Exception {
        createBookingApiFixture("cancel-ok", LocalDateTime.now().plusDays(1));

        mockMvc.perform(post("/api/bookings/cancel-ok-booking/cancel").param("customerId", "cancel-ok-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void bookingApiDeleteCancelWithValidCustomerIdSucceeds() throws Exception {
        createBookingApiFixture("cancel-delete-ok", LocalDateTime.now().plusDays(1));

        mockMvc.perform(delete("/api/bookings/cancel-delete-ok-booking").param("customerId", "cancel-delete-ok-user"))
                .andExpect(status().isNoContent());
    }

    @Test
    void bookingApiCancelWithoutCustomerIdReturnsBadRequest() throws Exception {
        createBookingApiFixture("cancel-missing-customer", LocalDateTime.now().plusDays(1));

        mockMvc.perform(post("/api/bookings/cancel-missing-customer-booking/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("customerId")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("missing")))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/bookings/cancel-missing-customer-booking/cancel"));
    }

    @Test
    void bookingApiCancelWithBlankCustomerIdReturnsBadRequest() throws Exception {
        createBookingApiFixture("cancel-blank-customer", LocalDateTime.now().plusDays(1));

        mockMvc.perform(post("/api/bookings/cancel-blank-customer-booking/cancel").param("customerId", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Customer ID is required")))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/bookings/cancel-blank-customer-booking/cancel"));
    }

    @Test
    void bookingApiCancelWrongCustomerRejected() throws Exception {
        createBookingApiFixture("cancel-wrong-owner", LocalDateTime.now().plusDays(1));

        mockMvc.perform(post("/api/bookings/cancel-wrong-owner-booking/cancel").param("customerId", "other-user"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("owning customer")));
    }

    @Test
    void bookingApiCancelAlreadyCancelledBookingRejected() throws Exception {
        createBookingApiFixture("cancel-again", LocalDateTime.now().plusDays(1));
        mockMvc.perform(post("/api/bookings/cancel-again-booking/cancel").param("customerId", "cancel-again-user"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings/cancel-again-booking/cancel").param("customerId", "cancel-again-user"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cancelled again")));
    }

    @Test
    void openApiDocsEndpointAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void serviceApiGetAllReturnsUnfilteredCatalog() throws Exception {
        createService("catalog-all-active", "Exterior Wash", true);
        createService("catalog-all-inactive", "Interior Wash", false);

        String response = mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(response.contains("catalog-all-active"));
        org.junit.jupiter.api.Assertions.assertTrue(response.contains("catalog-all-inactive"));
    }

    @Test
    void serviceApiGetAllFiltersActiveServices() throws Exception {
        createService("catalog-active-only", "Deluxe Wash", true);
        createService("catalog-active-excluded", "Archived Wash", false);

        String response = mockMvc.perform(get("/api/services").param("active", "true"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(response.contains("catalog-active-only"));
        org.junit.jupiter.api.Assertions.assertFalse(response.contains("catalog-active-excluded"));
    }

    @Test
    void serviceApiGetAllFiltersInactiveServices() throws Exception {
        createService("catalog-inactive-excluded", "Express Wash", true);
        createService("catalog-inactive-only", "Seasonal Wash", false);

        String response = mockMvc.perform(get("/api/services").param("active", "false"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(response.contains("catalog-inactive-only"));
        org.junit.jupiter.api.Assertions.assertFalse(response.contains("catalog-inactive-excluded"));
    }

    private void createService(String serviceId, String serviceName, boolean active) throws Exception {
        Map<String, Object> service = new HashMap<>();
        service.put("serviceId", serviceId);
        service.put("serviceName", serviceName);
        service.put("description", serviceName + " description");
        service.put("price", BigDecimal.valueOf(100));
        service.put("estimatedDurationMin", 30);

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(service)))
                .andExpect(status().isCreated());

        if (!active) {
            mockMvc.perform(post("/api/services/" + serviceId + "/deactivate"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void bookingAndQueueWorkflow_shouldCompleteSuccessfully() throws Exception {
        String prefix = "workflow-success";

        createBookingWorkflowFixture(prefix);

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", prefix + "-booking");
        booking.put("userId", prefix + "-user");
        booking.put("vehicleId", prefix + "-vehicle");
        booking.put("serviceId", prefix + "-service");
        booking.put("scheduledDateTime", LocalDateTime.now().plusDays(2).toString());
        booking.put("specialRequest", "workflow coverage");

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(booking)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(prefix + "-booking"))
                .andExpect(jsonPath("$.user.userId").value(prefix + "-user"))
                .andExpect(jsonPath("$.vehicle.vehicleId").value(prefix + "-vehicle"))
                .andExpect(jsonPath("$.service.serviceId").value(prefix + "-service"))
                .andExpect(jsonPath("$.status").value("CREATED"));

        mockMvc.perform(post("/api/bookings/" + prefix + "-booking/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(prefix + "-booking"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Map<String, Object> queueEntry = new HashMap<>();
        queueEntry.put("queueEntryId", prefix + "-queue-entry");
        queueEntry.put("bookingId", prefix + "-booking");
        queueEntry.put("serviceId", prefix + "-service");
        queueEntry.put("position", 1);

        mockMvc.perform(post("/api/queue-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(queueEntry)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.queueEntryId").value(prefix + "-queue-entry"))
                .andExpect(jsonPath("$.booking.bookingId").value(prefix + "-booking"))
                .andExpect(jsonPath("$.service.serviceId").value(prefix + "-service"))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.queueStatus").value("WAITING"));

        mockMvc.perform(post("/api/queue-entries/" + prefix + "-queue-entry/call-next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueStatus").value("CALLED"))
                .andExpect(jsonPath("$.calledAt").exists());

        mockMvc.perform(post("/api/queue-entries/" + prefix + "-queue-entry/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.startedAt").exists());

        mockMvc.perform(post("/api/queue-entries/" + prefix + "-queue-entry/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.estimatedWaitMin").value(0))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    void queueWorkflow_shouldRejectInvalidTransition() throws Exception {
        String prefix = "workflow-invalid";

        createBookingApiFixture(prefix, LocalDateTime.now().plusDays(2));
        mockMvc.perform(post("/api/bookings/" + prefix + "-booking/confirm"))
                .andExpect(status().isOk());

        Map<String, Object> queueEntry = new HashMap<>();
        queueEntry.put("queueEntryId", prefix + "-queue-entry");
        queueEntry.put("bookingId", prefix + "-booking");
        queueEntry.put("serviceId", prefix + "-service");
        queueEntry.put("position", 1);

        mockMvc.perform(post("/api/queue-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(queueEntry)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.queueStatus").value("WAITING"));

        mockMvc.perform(post("/api/queue-entries/" + prefix + "-queue-entry/complete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cannot be completed before it has started")));

        mockMvc.perform(get("/api/queue-entries/" + prefix + "-queue-entry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueStatus").value("WAITING"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    private void createBookingWorkflowFixture(String prefix) throws Exception {
        Map<String, Object> user = Map.of(
                "userId", prefix + "-user",
                "fullName", "Workflow User",
                "email", prefix + "@test.com",
                "phone", "123",
                "passwordHash", "x"
        );
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(prefix + "-user"));

        Map<String, Object> vehicle = Map.of(
                "userId", prefix + "-user",
                "vehicleId", prefix + "-vehicle",
                "plateNumber", prefix + "-plate",
                "vehicleType", "SUV",
                "brand", "Toyota",
                "model", "Rav4",
                "color", "Black",
                "notes", ""
        );
        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehicle)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleId").value(prefix + "-vehicle"))
                .andExpect(jsonPath("$.userId").value(prefix + "-user"));

        Map<String, Object> service = new HashMap<>();
        service.put("serviceId", prefix + "-service");
        service.put("serviceName", "Workflow Service");
        service.put("description", "workflow service");
        service.put("price", BigDecimal.valueOf(250));
        service.put("estimatedDurationMin", 30);
        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(service)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceId").value(prefix + "-service"));
        mockMvc.perform(post("/api/services/" + prefix + "-service/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void bookingValidationErrorIncludesFieldLevelMessage() throws Exception {
        String invalidBooking = """
                {
                  "bookingId": "",
                  "userId": "",
                  "vehicleId": "v-test",
                  "serviceId": "s-test",
                  "scheduledDateTime": null,
                  "specialRequest": "validation test"
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBooking))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("bookingId")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("userId")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("scheduledDateTime")))
                .andExpect(jsonPath("$.path").value("/api/bookings"));
    }

    @Test
    void notificationApiReturnsRecentNotificationsForUser() throws Exception {
        String prefix = "notifications-recent";
        createBookingApiFixture(prefix, LocalDateTime.now().plusDays(2));
        mockMvc.perform(post("/api/bookings/" + prefix + "-booking/confirm"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications/user/" + prefix + "-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("BOOKING_CONFIRMED"))
                .andExpect(jsonPath("$[0].message").value("Your booking has been confirmed."))
                .andExpect(jsonPath("$[0].user.userId").value(prefix + "-user"));
    }

    @Test
    void notificationApiReturnsOnlyRequestedUsersNotifications() throws Exception {
        String firstPrefix = "notifications-owner";
        String secondPrefix = "notifications-other";
        createBookingApiFixture(firstPrefix, LocalDateTime.now().plusDays(2));
        createBookingApiFixture(secondPrefix, LocalDateTime.now().plusDays(2));
        mockMvc.perform(post("/api/bookings/" + firstPrefix + "-booking/confirm"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bookings/" + secondPrefix + "-booking/confirm"))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/api/notifications/user/" + firstPrefix + "-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user.userId").value(firstPrefix + "-user"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(response.contains(firstPrefix + "-booking"));
        org.junit.jupiter.api.Assertions.assertFalse(response.contains(secondPrefix + "-booking"));
    }

    @Test
    void notificationApiReturnsEmptyListWhenUserHasNoNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications/user/no-notifications-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void dailySummaryReportReturnsExpectedTotals() throws Exception {
        String prefix = "daily-summary";
        LocalDateTime reportDateTime = LocalDateTime.now().plusDays(12).withHour(9).withMinute(0).withSecond(0).withNano(0);
        createBookingApiFixture(prefix + "-confirmed", reportDateTime);
        createBookingApiFixture(prefix + "-cancelled", reportDateTime.plusHours(1));
        createBookingApiFixture(prefix + "-waiting", reportDateTime.plusHours(2));
        createBookingApiFixture(prefix + "-called", reportDateTime.plusHours(3));
        createBookingApiFixture(prefix + "-progress", reportDateTime.plusHours(4));
        createBookingApiFixture(prefix + "-completed", reportDateTime.plusHours(5));
        createBookingApiFixture(prefix + "-other-day", reportDateTime.plusDays(1));

        mockMvc.perform(post("/api/bookings/" + prefix + "-confirmed-booking/confirm"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bookings/" + prefix + "-cancelled-booking/cancel")
                        .param("customerId", prefix + "-cancelled-user"))
                .andExpect(status().isOk());

        createQueueEntry(prefix + "-waiting");
        createQueueEntry(prefix + "-called");
        mockMvc.perform(post("/api/queue-entries/" + prefix + "-called-queue-entry/call-next"))
                .andExpect(status().isOk());
        createQueueEntry(prefix + "-progress");
        mockMvc.perform(post("/api/queue-entries/" + prefix + "-progress-queue-entry/start"))
                .andExpect(status().isOk());
        createQueueEntry(prefix + "-completed");
        mockMvc.perform(post("/api/queue-entries/" + prefix + "-completed-queue-entry/start"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/queue-entries/" + prefix + "-completed-queue-entry/complete"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/reports/daily-summary")
                        .param("date", reportDateTime.toLocalDate().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportDate").value(reportDateTime.toLocalDate().toString()))
                .andExpect(jsonPath("$.totalBookings").value(6))
                .andExpect(jsonPath("$.confirmedBookings").value(1))
                .andExpect(jsonPath("$.cancelledBookings").value(1))
                .andExpect(jsonPath("$.completedBookings").value(0))
                .andExpect(jsonPath("$.totalQueueEntries").value(4))
                .andExpect(jsonPath("$.waitingQueueEntries").value(1))
                .andExpect(jsonPath("$.calledQueueEntries").value(1))
                .andExpect(jsonPath("$.inProgressQueueEntries").value(1))
                .andExpect(jsonPath("$.completedQueueEntries").value(1))
                .andExpect(jsonPath("$.pendingWorkload").value(5));
    }

    @Test
    void dailySummaryReportDateWithNoDataReturnsZeroTotals() throws Exception {
        LocalDateTime reportDateTime = LocalDateTime.now().plusDays(30);

        mockMvc.perform(get("/api/reports/daily-summary")
                        .param("date", reportDateTime.toLocalDate().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportDate").value(reportDateTime.toLocalDate().toString()))
                .andExpect(jsonPath("$.totalBookings").value(0))
                .andExpect(jsonPath("$.totalQueueEntries").value(0))
                .andExpect(jsonPath("$.pendingWorkload").value(0));
    }

    @Test
    void dailySummaryReportMissingDateReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/reports/daily-summary"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void dailySummaryReportInvalidDateReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/reports/daily-summary").param("date", "06/28/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private void createQueueEntry(String prefix) throws Exception {
        Map<String, Object> queueEntry = new HashMap<>();
        queueEntry.put("queueEntryId", prefix + "-queue-entry");
        queueEntry.put("bookingId", prefix + "-booking");
        queueEntry.put("serviceId", prefix + "-service");
        queueEntry.put("position", 1);

        mockMvc.perform(post("/api/queue-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(queueEntry)))
                .andExpect(status().isCreated());
    }

    private void createBookingApiFixture(String prefix, LocalDateTime scheduledDateTime) throws Exception {
        Map<String, Object> user = Map.of(
                "userId", prefix + "-user",
                "fullName", prefix + " User",
                "email", prefix + "@test.com",
                "phone", "123",
                "passwordHash", "x"
        );
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isCreated());

        Map<String, Object> vehicle = Map.of(
                "userId", prefix + "-user",
                "vehicleId", prefix + "-vehicle",
                "plateNumber", prefix + "-plate",
                "vehicleType", "SUV",
                "brand", "Toyota",
                "model", "Rav4",
                "color", "Black",
                "notes", ""
        );
        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehicle)))
                .andExpect(status().isCreated());

        Map<String, Object> service = new HashMap<>();
        service.put("serviceId", prefix + "-service");
        service.put("serviceName", prefix + " Service");
        service.put("description", "premium");
        service.put("price", BigDecimal.valueOf(300));
        service.put("estimatedDurationMin", 45);
        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(service)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/services/" + prefix + "-service/activate"))
                .andExpect(status().isOk());

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", prefix + "-booking");
        booking.put("userId", prefix + "-user");
        booking.put("vehicleId", prefix + "-vehicle");
        booking.put("serviceId", prefix + "-service");
        booking.put("scheduledDateTime", scheduledDateTime.toString());
        booking.put("specialRequest", "none");
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(booking)))
                .andExpect(status().isCreated());
    }
}
