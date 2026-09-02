package com.carwash.notification.api;

import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.BookingApiFixture;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationWorkflowIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void notificationApiReturnsRecentNotificationsForUser() throws Exception {
        BookingApiFixture.CreatedBooking booking = fixture().createBooking(TestDates.futureDays(1));
        confirm(booking);
        mockMvc.perform(get("/api/notifications/user/{userId}", booking.resources().user().userId())
                        .with(authentication.platformAdminJwt())
                        .param("businessId", booking.resources().business().businessId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notificationId").value("notification-00000000000000000001"))
                .andExpect(jsonPath("$[0].type").value("BOOKING_CONFIRMED"))
                .andExpect(jsonPath("$[0].message").value("Your booking has been confirmed."))
                .andExpect(jsonPath("$[0].userId").value(booking.resources().user().userId()))
                .andExpect(jsonPath("$[0].branchId").value(booking.resources().branch().branchId()))
                .andExpect(jsonPath("$[0].serviceOfferingId").value(
                        booking.resources().offering().offeringId()))
                .andExpect(jsonPath("$[0].user").doesNotExist())
                .andExpect(jsonPath("$[0].booking").doesNotExist());
    }

    @Test
    void notificationApiReturnsOnlyRequestedUsersNotifications() throws Exception {
        BookingApiFixture.CreatedBooking first = fixture().createBooking(TestDates.futureDays(2));
        BookingApiFixture.CreatedBooking second = fixture().createBooking(TestDates.futureDays(3));
        confirm(first);
        confirm(second);
        String response = mockMvc.perform(get("/api/notifications/user/{userId}", first.resources().user().userId())
                        .with(authentication.platformAdminJwt())
                        .param("businessId", first.resources().business().businessId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(response.contains(first.booking().bookingId()));
        assertFalse(response.contains(second.booking().bookingId()));
    }

    @Test
    void notificationApiReturnsEmptyListWhenUserHasNoNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications/user/{userId}", ids.user())
                        .with(authentication.platformAdminJwt())
                        .param("businessId", ids.business()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void customerInboxPaginatesFiltersAndAppliesIdempotentReadLifecycle() throws Exception {
        BookingApiFixture.CreatedBooking booking = fixture().createBooking(TestDates.futureDays(4));
        confirm(booking);
        mockMvc.perform(post("/api/bookings/{id}/cancel", booking.booking().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        String userId = booking.resources().user().userId();

        JsonNode first = body(mockMvc.perform(get("/api/notifications/user/{userId}/inbox", userId)
                        .with(authentication.customerJwt(userId)).param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn().getResponse().getContentAsString());
        String firstId = first.path("notifications").get(0).path("notificationId").asText();
        String cursor = first.path("nextCursor").asText();

        JsonNode second = body(mockMvc.perform(get("/api/notifications/user/{userId}/inbox", userId)
                        .with(authentication.customerJwt(userId))
                        .param("limit", "1").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andReturn().getResponse().getContentAsString());
        assertFalse(firstId.equals(second.path("notifications").get(0).path("notificationId").asText()));

        JsonNode marked = body(mockMvc.perform(put("/api/notifications/{id}/read", firstId)
                        .with(authentication.customerJwt(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryStatus").value("READ"))
                .andExpect(jsonPath("$.readAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(put("/api/notifications/{id}/read", firstId)
                        .with(authentication.customerJwt(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").value(marked.path("readAt").asText()));

        mockMvc.perform(get("/api/notifications/user/{userId}/inbox", userId)
                        .with(authentication.customerJwt(userId)).param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].deliveryStatus").value("SENT"))
                .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(put("/api/notifications/user/{userId}/read-all", userId)
                        .with(authentication.customerJwt(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedCount").value(1))
                .andExpect(jsonPath("$.readAt").isNotEmpty());
        mockMvc.perform(put("/api/notifications/user/{userId}/read-all", userId)
                        .with(authentication.customerJwt(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedCount").value(0));
    }

    @Test
    void inboxAuthorizationUsesRecipientAndTenantPredicatesAtTheApplicationBoundary() throws Exception {
        BookingApiFixture.CreatedBooking booking = fixture().createBooking(TestDates.futureDays(5));
        confirm(booking);
        String userId = booking.resources().user().userId();
        String businessId = booking.resources().business().businessId();
        String notificationId = notificationId(userId, businessId);

        mockMvc.perform(get("/api/notifications/user/{userId}/inbox", userId)
                        .with(authentication.customerJwt("another-customer")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/notifications/user/{userId}/inbox", userId)
                        .with(authentication.customerJwt(userId)).param("businessId", businessId))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/notifications/user/{userId}/inbox", userId)
                        .with(authentication.tenantRoleJwt("owner-a", "BUSINESS_OWNER", businessId,
                                "PERM_NOTIFICATION_SELF_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications[0].notificationId").value(notificationId));
        mockMvc.perform(get("/api/notifications/user/{userId}/inbox", userId)
                        .with(authentication.tenantRoleJwt("owner-b", "BUSINESS_OWNER", "foreign-business",
                                "PERM_NOTIFICATION_SELF_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications").isEmpty())
                .andExpect(jsonPath("$.unreadCount").value(0));
        mockMvc.perform(get("/api/notifications/user/{userId}/inbox", userId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/notifications/user/{userId}/inbox", userId)
                        .with(authentication.platformAdminJwt()).param("businessId", businessId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications[0].notificationId").value(notificationId));

        mockMvc.perform(put("/api/notifications/{id}/read", notificationId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/notifications/user/{userId}/read-all", userId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void inboxRejectsInvalidCursorsAndReturnsOnlyThePrivacyAllowlist() throws Exception {
        BookingApiFixture.CreatedBooking booking = fixture().createBooking(TestDates.futureDays(6));
        confirm(booking);
        String userId = booking.resources().user().userId();

        mockMvc.perform(get("/api/notifications/user/{userId}/inbox", userId)
                        .with(authentication.customerJwt(userId)).param("cursor", "malformed"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/notifications/user/{userId}/inbox", userId)
                        .with(authentication.customerJwt(userId)).param("limit", "101"))
                .andExpect(status().isBadRequest());
        String response = mockMvc.perform(get("/api/notifications/user/{userId}/inbox", userId)
                        .with(authentication.customerJwt(userId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode notification = body(response).path("notifications").get(0);
        Set<String> fields = new HashSet<>();
        notification.properties().forEach(field -> fields.add(field.getKey()));
        assertEquals(Set.of("notificationId", "userId", "bookingId", "branchId", "serviceOfferingId",
                "type", "message", "channel", "sentAt", "readAt", "deliveryStatus"), fields);
        for (String sensitive : Set.of("user", "booking", "password", "email", "phone", "address",
                "role", "tenantMembership", "specialRequest", "version")) {
            assertFalse(response.contains("\"" + sensitive + "\""));
        }
    }

    @Test
    void missingAndForeignNotificationIdsAreIndistinguishableAndAuthenticationIsRequired() throws Exception {
        BookingApiFixture.CreatedBooking booking = fixture().createBooking(TestDates.futureDays(7));
        confirm(booking);
        String notificationId = notificationId(
                booking.resources().user().userId(), booking.resources().business().businessId());
        String foreign = mockMvc.perform(put("/api/notifications/{id}/read", notificationId)
                        .with(authentication.customerJwt("foreign-customer")))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(put("/api/notifications/{id}/read", "missing-notification")
                        .with(authentication.customerJwt("foreign-customer")))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(body(foreign).path("message").asText(), body(missing).path("message").asText());

        mockMvc.perform(get("/api/notifications/user/{userId}/inbox", booking.resources().user().userId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/notifications/{id}/read", notificationId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/notifications/user/{userId}/read-all",
                        booking.resources().user().userId()))
                .andExpect(status().isUnauthorized());
    }

    private BookingApiFixture fixture() {
        return new BookingApiFixture(api, ids);
    }

    private void confirm(BookingApiFixture.CreatedBooking booking) throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/confirm", booking.booking().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
    }

    private String notificationId(String userId, String businessId) throws Exception {
        String response = mockMvc.perform(get("/api/notifications/user/{userId}", userId)
                        .with(authentication.platformAdminJwt()).param("businessId", businessId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return body(response).get(0).path("notificationId").asText();
    }

    private JsonNode body(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
