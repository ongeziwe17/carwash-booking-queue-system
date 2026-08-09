package com.carwash.api;

import com.carwash.api.dto.CreateQueueEntryRequest;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.BookingApiFixture;
import com.carwash.testsupport.QueueFixtureBuilder;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportWorkflowIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void dailySummaryReportReturnsExpectedTotals() throws Exception {
        LocalDateTime reportDateTime = TestDates.futureDays(20);
        BookingApiFixture.CreatedBooking confirmed = createBooking(reportDateTime);
        BookingApiFixture.CreatedBooking cancelled = createBooking(reportDateTime.plusHours(1));
        BookingApiFixture.CreatedBooking waiting = createBooking(reportDateTime.plusHours(2));
        BookingApiFixture.CreatedBooking called = createBooking(reportDateTime.plusHours(3));
        BookingApiFixture.CreatedBooking progress = createBooking(reportDateTime.plusHours(4));
        BookingApiFixture.CreatedBooking completed = createBooking(reportDateTime.plusHours(5));
        createBooking(reportDateTime.plusDays(1));

        postBookingAction(confirmed, "confirm");
        mockMvc.perform(post("/api/bookings/{id}/cancel", cancelled.booking().bookingId())
                        .with(authentication.platformAdminJwt())
                        .param("customerId", cancelled.resources().user().userId()))
                .andExpect(status().isOk());

        createQueue(waiting);
        CreateQueueEntryRequest calledQueue = createQueue(called);
        postQueueAction(calledQueue, "call");
        CreateQueueEntryRequest progressQueue = createQueue(progress);
        postQueueAction(progressQueue, "call");
        postQueueAction(progressQueue, "start");
        CreateQueueEntryRequest completedQueue = createQueue(completed);
        postQueueAction(completedQueue, "call");
        postQueueAction(completedQueue, "start");
        postQueueAction(completedQueue, "complete");

        mockMvc.perform(get("/api/reports/daily-summary")
                        .with(authentication.platformAdminJwt())
                        .param("date", reportDateTime.toLocalDate().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportDate").value(reportDateTime.toLocalDate().toString()))
                .andExpect(jsonPath("$.totalBookings").value(6))
                .andExpect(jsonPath("$.confirmedBookings").value(3))
                .andExpect(jsonPath("$.cancelledBookings").value(1))
                .andExpect(jsonPath("$.completedBookings").value(1))
                .andExpect(jsonPath("$.totalQueueEntries").value(4))
                .andExpect(jsonPath("$.waitingQueueEntries").value(1))
                .andExpect(jsonPath("$.calledQueueEntries").value(1))
                .andExpect(jsonPath("$.inProgressQueueEntries").value(1))
                .andExpect(jsonPath("$.completedQueueEntries").value(1))
                .andExpect(jsonPath("$.pendingWorkload").value(4));
    }

    @Test
    void dailySummaryReportDateWithNoDataReturnsZeroTotals() throws Exception {
        mockMvc.perform(get("/api/reports/daily-summary")
                        .with(authentication.platformAdminJwt())
                        .param("date", TestDates.futureDays(30).toLocalDate().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBookings").value(0))
                .andExpect(jsonPath("$.totalQueueEntries").value(0))
                .andExpect(jsonPath("$.pendingWorkload").value(0));
    }

    @Test
    void dailySummaryReportMissingDateReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/reports/daily-summary").with(authentication.platformAdminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void dailySummaryReportInvalidDateReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/reports/daily-summary")
                        .with(authentication.platformAdminJwt())
                        .param("date", "06/28/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private BookingApiFixture.CreatedBooking createBooking(LocalDateTime dateTime) throws Exception {
        return new BookingApiFixture(api, ids).createBooking(dateTime);
    }

    private CreateQueueEntryRequest createQueue(BookingApiFixture.CreatedBooking booking) throws Exception {
        postBookingAction(booking, "confirm");
        CreateQueueEntryRequest request = QueueFixtureBuilder.valid(ids, booking.booking().bookingId(),
                booking.resources().service().serviceId()).build();
        api.createQueueEntry(request).andExpect(status().isCreated());
        return request;
    }

    private void postBookingAction(BookingApiFixture.CreatedBooking booking, String action) throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/" + action, booking.booking().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
    }

    private void postQueueAction(CreateQueueEntryRequest queue, String action) throws Exception {
        mockMvc.perform(post("/api/queue-entries/{id}/" + action, queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
    }
}
