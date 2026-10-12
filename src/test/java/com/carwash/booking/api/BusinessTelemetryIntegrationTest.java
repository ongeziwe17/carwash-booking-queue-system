package com.carwash.booking.api;

import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.BookingApiFixture;
import com.carwash.testsupport.TestDates;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BusinessTelemetryIntegrationTest extends ApiIntegrationTestSupport {
    @Autowired MeterRegistry meters;
    @Test void composedFinalServiceCountsBookingAndNotificationOnceAndFailuresSeparately() throws Exception {
        double created = outcome("booking_create", "committed");
        double confirmed = outcome("booking_confirm", "committed");
        double notifications = outcome("notification_create", "committed");
        var booking = new BookingApiFixture(api, ids).createBooking(TestDates.futureDays(100));
        assertEquals(created + 1, outcome("booking_create", "committed"));
        mockMvc.perform(post("/api/bookings/{id}/confirm", booking.booking().bookingId())
                        .with(authentication.platformAdminJwt())).andExpect(status().isOk());
        assertEquals(confirmed + 1, outcome("booking_confirm", "committed"));
        assertTrue(outcome("notification_create", "committed") > notifications);
        double failed = outcome("booking_confirm", "failed");
        mockMvc.perform(post("/api/bookings/{id}/confirm", "unknown-booking")
                        .with(authentication.platformAdminJwt())).andExpect(status().isNotFound());
        assertEquals(failed + 1, outcome("booking_confirm", "failed"));
        assertEquals(confirmed + 1, outcome("booking_confirm", "committed"));
    }
    private double outcome(String operation, String outcome) {
        var counter = meters.find("carwash.workflow.outcomes").tags("operation", operation, "outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }
}
