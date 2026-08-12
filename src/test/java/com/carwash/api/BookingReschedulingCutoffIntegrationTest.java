package com.carwash.api;

import com.carwash.booking.domain.Booking;

import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.BookingApiFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(BookingReschedulingCutoffIntegrationTest.FixedCutoffClockConfiguration.class)
@TestPropertySource(properties = "carwash.policy.booking.cancellation-window=PT2H")
class BookingReschedulingCutoffIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired Clock clock;

    @Test
    void rescheduleInsideConfiguredCutoffReturnsStandardBusinessRuleError() throws Exception {
        LocalDateTime currentSchedule = LocalDateTime.now(clock).plusHours(1);
        BookingApiFixture.CreatedBooking created = new BookingApiFixture(api, ids).createBooking(currentSchedule);

        mockMvc.perform(post("/api/bookings/{id}/reschedule", created.booking().bookingId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduledDateTime", LocalDateTime.now(clock).plusHours(3).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Booking rescheduling window has closed"))
                .andExpect(jsonPath("$.path").value(
                        "/api/bookings/" + created.booking().bookingId() + "/reschedule"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedCutoffClockConfiguration {
        @Bean
        @Primary
        Clock fixedCutoffClock() {
            return Clock.fixed(Instant.parse("2090-01-15T08:00:00Z"), ZoneOffset.UTC);
        }
    }
}
