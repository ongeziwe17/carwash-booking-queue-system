package com.carwash.api;

import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.BookingApiFixture;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationWorkflowIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void notificationApiReturnsRecentNotificationsForUser() throws Exception {
        BookingApiFixture.CreatedBooking booking = fixture().createBooking(TestDates.futureDays(1));
        confirm(booking);
        mockMvc.perform(get("/api/notifications/user/{userId}", booking.resources().user().userId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notificationId").value("notification-00000000000000000001"))
                .andExpect(jsonPath("$[0].type").value("BOOKING_CONFIRMED"))
                .andExpect(jsonPath("$[0].message").value("Your booking has been confirmed."))
                .andExpect(jsonPath("$[0].user.userId").value(booking.resources().user().userId()));
    }

    @Test
    void notificationApiReturnsOnlyRequestedUsersNotifications() throws Exception {
        BookingApiFixture.CreatedBooking first = fixture().createBooking(TestDates.futureDays(2));
        BookingApiFixture.CreatedBooking second = fixture().createBooking(TestDates.futureDays(3));
        confirm(first);
        confirm(second);
        String response = mockMvc.perform(get("/api/notifications/user/{userId}", first.resources().user().userId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(response.contains(first.booking().bookingId()));
        assertFalse(response.contains(second.booking().bookingId()));
    }

    @Test
    void notificationApiReturnsEmptyListWhenUserHasNoNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications/user/{userId}", ids.user())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private BookingApiFixture fixture() {
        return new BookingApiFixture(api, ids);
    }

    private void confirm(BookingApiFixture.CreatedBooking booking) throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/confirm", booking.booking().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
    }
}
