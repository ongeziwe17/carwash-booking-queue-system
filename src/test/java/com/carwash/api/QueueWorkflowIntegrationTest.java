package com.carwash.api;

import com.carwash.api.dto.CreateQueueEntryRequest;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.BookingApiFixture;
import com.carwash.testsupport.QueueFixtureBuilder;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QueueWorkflowIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void createQueueEntryRejectsUnknownBooking() throws Exception {
        BookingApiFixture.CreatedBooking booking = bookingFixture().createBooking(TestDates.futureDays(1));
        CreateQueueEntryRequest request = QueueFixtureBuilder.valid(ids, ids.booking(),
                booking.resources().service().serviceId()).build();
        api.createQueueEntry(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Booking not found")))
                .andExpect(jsonPath("$.path").value("/api/queue-entries"));
    }

    @Test
    void createQueueEntryRejectsUnknownService() throws Exception {
        BookingApiFixture.CreatedBooking booking = bookingFixture().createBooking(TestDates.futureDays(2));
        confirmBooking(booking);
        CreateQueueEntryRequest request = QueueFixtureBuilder.valid(ids, booking.booking().bookingId(), ids.service()).build();
        api.createQueueEntry(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Service not found")))
                .andExpect(jsonPath("$.path").value("/api/queue-entries"));
    }

    @Test
    void createQueueEntryRejectsInvalidPosition() throws Exception {
        BookingApiFixture.CreatedBooking booking = bookingFixture().createBooking(TestDates.futureDays(3));
        CreateQueueEntryRequest request = QueueFixtureBuilder.valid(ids, booking.booking().bookingId(),
                        booking.resources().service().serviceId())
                .position(0).build();
        api.createQueueEntry(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("position"));
    }

    @Test
    void createQueueEntryRejectsMismatchedBookingService() throws Exception {
        BookingApiFixture.CreatedBooking first = bookingFixture().createBooking(TestDates.futureDays(4));
        BookingApiFixture.CreatedBooking second = bookingFixture().createBooking(TestDates.futureDays(5));
        confirmBooking(first);
        CreateQueueEntryRequest request = QueueFixtureBuilder.valid(ids, first.booking().bookingId(),
                second.resources().service().serviceId()).build();
        api.createQueueEntry(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("must match booking service")))
                .andExpect(jsonPath("$.path").value("/api/queue-entries"));
    }

    @Test
    void createQueueEntryRejectsCreatedBooking() throws Exception {
        BookingApiFixture.CreatedBooking booking = bookingFixture().createBooking(TestDates.futureDays(10));
        CreateQueueEntryRequest request = QueueFixtureBuilder.valid(ids, booking.booking().bookingId(),
                booking.resources().service().serviceId()).build();

        assertBusinessRule(api.createQueueEntry(request), "Only confirmed bookings can join the queue");
    }

    @Test
    void createQueueEntryRejectsCancelledBooking() throws Exception {
        BookingApiFixture.CreatedBooking booking = bookingFixture().createBooking(TestDates.futureDays(11));
        mockMvc.perform(post("/api/bookings/{id}/cancel", booking.booking().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        CreateQueueEntryRequest request = QueueFixtureBuilder.valid(ids, booking.booking().bookingId(),
                booking.resources().service().serviceId()).build();

        assertBusinessRule(api.createQueueEntry(request), "Only confirmed bookings can join the queue");
    }

    @Test
    void createQueueEntryRejectsSecondActiveEntryForBooking() throws Exception {
        BookingApiFixture.CreatedBooking booking = bookingFixture().createBooking(TestDates.futureDays(12));
        CreateQueueEntryRequest first = createQueue(booking);
        CreateQueueEntryRequest second = QueueFixtureBuilder.valid(ids, booking.booking().bookingId(),
                booking.resources().service().serviceId()).position(2).build();

        assertBusinessRule(api.createQueueEntry(second), "Booking already has an active queue entry");
        mockMvc.perform(get("/api/queue-entries/{id}", first.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueEntryId").value(first.queueEntryId()))
                .andExpect(jsonPath("$.queueStatus").value("WAITING"))
                .andExpect(jsonPath("$.position").value(1));
    }

    @Test
    void createQueueEntryRejectsInactiveMatchingService() throws Exception {
        BookingApiFixture.CreatedBooking booking = bookingFixture().createBooking(TestDates.futureDays(13));
        confirmBooking(booking);
        api.deactivateService(booking.resources().service().serviceId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        CreateQueueEntryRequest request = QueueFixtureBuilder.valid(ids, booking.booking().bookingId(),
                booking.resources().service().serviceId()).build();

        assertBusinessRule(api.createQueueEntry(request), "Inactive service cannot join the queue");
    }

    @Test
    void queueEntryRequestValidationRejectsBlankIdsAndInvalidPositions() throws Exception {
        String queueId = ids.queueEntry();
        String bookingId = ids.booking();
        String serviceId = ids.service();
        List<QueueValidationCase> cases = List.of(
                new QueueValidationCase(Map.of("queueEntryId", " ", "bookingId", bookingId,
                        "serviceId", serviceId, "position", 1), "queueEntryId"),
                new QueueValidationCase(Map.of("queueEntryId", queueId, "bookingId", " ",
                        "serviceId", serviceId, "position", 1), "bookingId"),
                new QueueValidationCase(Map.of("queueEntryId", queueId, "bookingId", bookingId,
                        "serviceId", " ", "position", 1), "serviceId"),
                new QueueValidationCase(Map.of("queueEntryId", queueId, "bookingId", bookingId,
                        "serviceId", serviceId), "position"),
                new QueueValidationCase(Map.of("queueEntryId", queueId, "bookingId", bookingId,
                        "serviceId", serviceId, "position", 0), "position"),
                new QueueValidationCase(Map.of("queueEntryId", queueId, "bookingId", bookingId,
                        "serviceId", serviceId, "position", -1), "position")
        );

        for (QueueValidationCase validationCase : cases) {
            mockMvc.perform(post("/api/queue-entries")
                            .with(authentication.platformAdminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validationCase.body())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.message").value("Request validation failed"))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == '" + validationCase.field() + "')]").exists());
        }
    }

    @Test
    void bookingAndQueueWorkflowCompletesSuccessfully() throws Exception {
        BookingApiFixture.CreatedBooking booking = bookingFixture().createBooking(TestDates.futureDays(6));
        CreateQueueEntryRequest queue = createQueue(booking);
        mockMvc.perform(post("/api/queue-entries/{id}/call-next", queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.queueStatus").value("CALLED"));
        mockMvc.perform(post("/api/queue-entries/{id}/start", queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.queueStatus").value("IN_PROGRESS"));
        mockMvc.perform(post("/api/queue-entries/{id}/complete", queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.estimatedWaitMin").value(0))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    void queueWorkflowRejectsInvalidTransition() throws Exception {
        BookingApiFixture.CreatedBooking booking = bookingFixture().createBooking(TestDates.futureDays(7));
        CreateQueueEntryRequest queue = createQueue(booking);
        mockMvc.perform(post("/api/queue-entries/{id}/complete", queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("cannot be completed before it has started")));
        mockMvc.perform(get("/api/queue-entries/{id}", queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueStatus").value("WAITING"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void queueWorkflowRejectsStartBeforeCall() throws Exception {
        BookingApiFixture.CreatedBooking booking = bookingFixture().createBooking(TestDates.futureDays(8));
        CreateQueueEntryRequest queue = createQueue(booking);
        mockMvc.perform(post("/api/queue-entries/{id}/start", queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("cannot start service")));
    }

    @Test
    void queueWorkflowRejectsAlreadyCompletedTransitions() throws Exception {
        BookingApiFixture.CreatedBooking booking = bookingFixture().createBooking(TestDates.futureDays(9));
        CreateQueueEntryRequest queue = createQueue(booking);
        mockMvc.perform(post("/api/queue-entries/{id}/call-next", queue.queueEntryId())
                        .with(authentication.platformAdminJwt())).andExpect(status().isOk());
        mockMvc.perform(post("/api/queue-entries/{id}/start", queue.queueEntryId())
                        .with(authentication.platformAdminJwt())).andExpect(status().isOk());
        mockMvc.perform(post("/api/queue-entries/{id}/complete", queue.queueEntryId())
                        .with(authentication.platformAdminJwt())).andExpect(status().isOk());

        mockMvc.perform(post("/api/queue-entries/{id}/call-next", queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("cannot be called")));
        mockMvc.perform(post("/api/queue-entries/{id}/start", queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("cannot start service")));
        mockMvc.perform(post("/api/queue-entries/{id}/complete", queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("cannot be completed")));
    }

    private BookingApiFixture bookingFixture() {
        return new BookingApiFixture(api, ids);
    }

    private CreateQueueEntryRequest createQueue(BookingApiFixture.CreatedBooking booking) throws Exception {
        confirmBooking(booking);
        CreateQueueEntryRequest queue = QueueFixtureBuilder.valid(ids, booking.booking().bookingId(),
                booking.resources().service().serviceId()).build();
        api.createQueueEntry(queue)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.queueEntryId").value(queue.queueEntryId()))
                .andExpect(jsonPath("$.queueStatus").value("WAITING"));
        return queue;
    }

    private void confirmBooking(BookingApiFixture.CreatedBooking booking) throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/confirm", booking.booking().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    private void assertBusinessRule(org.springframework.test.web.servlet.ResultActions action, String message)
            throws Exception {
        action.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value("/api/queue-entries"));
    }

    private record QueueValidationCase(Map<String, Object> body, String field) {
    }
}
