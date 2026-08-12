package com.carwash.api;

import com.carwash.catalog.domain.Service;

import com.carwash.booking.api.dto.CreateBookingRequest;
import com.carwash.queue.api.dto.CreateQueueEntryRequest;
import com.carwash.catalog.api.dto.CreateServiceRequest;
import com.carwash.identity.api.dto.CreateUserRequest;
import com.carwash.vehicle.api.dto.CreateVehicleRequest;
import com.carwash.booking.domain.Booking;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.BookingApiFixture;
import com.carwash.testsupport.BookingFixtureBuilder;
import com.carwash.testsupport.QueueFixtureBuilder;
import com.carwash.testsupport.ServiceFixtureBuilder;
import com.carwash.testsupport.TestDates;
import com.carwash.testsupport.UserFixtureBuilder;
import com.carwash.testsupport.VehicleFixtureBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QueueWorkflowIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    private BookingRepository bookingRepository;

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
    void createQueueEntryRejectsObsoleteClientPosition() throws Exception {
        BookingApiFixture.CreatedBooking booking = bookingFixture().createBooking(TestDates.futureDays(3));
        Map<String, Object> obsoleteRequest = Map.of(
                "queueEntryId", ids.queueEntry(),
                "bookingId", booking.booking().bookingId(),
                "serviceId", booking.resources().service().serviceId(),
                "position", 99);

        mockMvc.perform(post("/api/queue-entries")
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(obsoleteRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed or invalid request body"))
                .andExpect(jsonPath("$.path").value("/api/queue-entries"));
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
                booking.resources().service().serviceId()).build();

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
    void queueEntryRequestValidationRejectsBlankIds() throws Exception {
        String queueId = ids.queueEntry();
        String bookingId = ids.booking();
        String serviceId = ids.service();
        List<QueueValidationCase> cases = List.of(
                new QueueValidationCase(Map.of("queueEntryId", " ", "bookingId", bookingId,
                        "serviceId", serviceId), "queueEntryId"),
                new QueueValidationCase(Map.of("queueEntryId", queueId, "bookingId", " ",
                        "serviceId", serviceId), "bookingId"),
                new QueueValidationCase(Map.of("queueEntryId", queueId, "bookingId", bookingId,
                        "serviceId", " "), "serviceId")
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
    void serverAppendsEntriesAndCalculatesCumulativeWaitsAcrossServices() throws Exception {
        CreateQueueEntryRequest first = createQueue(createBookingWithDuration(10, 20), 1, 0);
        CreateQueueEntryRequest second = createQueue(createBookingWithDuration(25, 21), 2, 10);
        CreateQueueEntryRequest third = createQueue(createBookingWithDuration(15, 22), 3, 35);

        mockMvc.perform(get("/api/queue-entries").with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].queueEntryId").value(first.queueEntryId()))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].estimatedWaitMin").value(0))
                .andExpect(jsonPath("$[1].queueEntryId").value(second.queueEntryId()))
                .andExpect(jsonPath("$[1].position").value(2))
                .andExpect(jsonPath("$[1].estimatedWaitMin").value(10))
                .andExpect(jsonPath("$[2].queueEntryId").value(third.queueEntryId()))
                .andExpect(jsonPath("$[2].position").value(3))
                .andExpect(jsonPath("$[2].estimatedWaitMin").value(35));
    }

    @Test
    void completionClosesActiveGapAndRecalculatesLaterWaits() throws Exception {
        CreateQueueEntryRequest first = createQueue(createBookingWithDuration(10, 23), 1, 0);
        CreateQueueEntryRequest second = createQueue(createBookingWithDuration(25, 24), 2, 10);
        CreateQueueEntryRequest third = createQueue(createBookingWithDuration(15, 25), 3, 35);
        postQueueAction(first, "call");
        postQueueAction(first, "start");

        mockMvc.perform(post("/api/queue-entries/{id}/complete", first.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.estimatedWaitMin").value(0));

        mockMvc.perform(get("/api/queue-entries").with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].queueEntryId").value(second.queueEntryId()))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].estimatedWaitMin").value(0))
                .andExpect(jsonPath("$[1].queueEntryId").value(third.queueEntryId()))
                .andExpect(jsonPath("$[1].position").value(2))
                .andExpect(jsonPath("$[1].estimatedWaitMin").value(25))
                .andExpect(jsonPath("$[2].queueEntryId").value(first.queueEntryId()))
                .andExpect(jsonPath("$[2].queueStatus").value("COMPLETED"));
    }

    @Test
    void deletionClosesGapAndRecalculatesLaterWaits() throws Exception {
        CreateQueueEntryRequest first = createQueue(createBookingWithDuration(10, 26), 1, 0);
        CreateQueueEntryRequest second = createQueue(createBookingWithDuration(25, 27), 2, 10);
        CreateQueueEntryRequest third = createQueue(createBookingWithDuration(15, 28), 3, 35);

        mockMvc.perform(delete("/api/queue-entries/{id}", second.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/queue-entries").with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].queueEntryId").value(first.queueEntryId()))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].estimatedWaitMin").value(0))
                .andExpect(jsonPath("$[1].queueEntryId").value(third.queueEntryId()))
                .andExpect(jsonPath("$[1].position").value(2))
                .andExpect(jsonPath("$[1].estimatedWaitMin").value(10));
    }

    @Test
    void manualMovementRebalancesAllAffectedEntries() throws Exception {
        CreateQueueEntryRequest first = createQueue(createBookingWithDuration(10, 29), 1, 0);
        CreateQueueEntryRequest second = createQueue(createBookingWithDuration(20, 30), 2, 10);
        CreateQueueEntryRequest third = createQueue(createBookingWithDuration(30, 31), 3, 30);

        mockMvc.perform(put("/api/queue-entries/{id}/position", third.queueEntryId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueEntryId").value(third.queueEntryId()))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.estimatedWaitMin").value(0));

        mockMvc.perform(get("/api/queue-entries").with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].queueEntryId").value(third.queueEntryId()))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].estimatedWaitMin").value(0))
                .andExpect(jsonPath("$[1].queueEntryId").value(first.queueEntryId()))
                .andExpect(jsonPath("$[1].position").value(2))
                .andExpect(jsonPath("$[1].estimatedWaitMin").value(30))
                .andExpect(jsonPath("$[2].queueEntryId").value(second.queueEntryId()))
                .andExpect(jsonPath("$[2].position").value(3))
                .andExpect(jsonPath("$[2].estimatedWaitMin").value(40));
    }

    @Test
    void manualMovementValidationPreservesStandardErrors() throws Exception {
        CreateQueueEntryRequest queue = createQueue(createBookingWithDuration(10, 32), 1, 0);

        assertPositionValidation(queue.queueEntryId(), "{\"position\":null}");
        assertPositionValidation(queue.queueEntryId(), "{\"position\":0}");
        assertPositionValidation(queue.queueEntryId(), "{\"position\":-1}");

        mockMvc.perform(put("/api/queue-entries/{id}/position", queue.queueEntryId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Queue position exceeds active queue size"));

        mockMvc.perform(put("/api/queue-entries/{id}/position", ids.queueEntry())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        postQueueAction(queue, "call");
        mockMvc.perform(put("/api/queue-entries/{id}/position", queue.queueEntryId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Only waiting queue entries can be repositioned"));
    }

    @Test
    void bookingAndQueueWorkflowCompletesSuccessfully() throws Exception {
        BookingApiFixture.CreatedBooking booking = bookingFixture().createBooking(TestDates.futureDays(6));
        CreateQueueEntryRequest queue = createQueue(booking);
        mockMvc.perform(post("/api/queue-entries/{id}/call", queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueStatus").value("CALLED"))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));
        mockMvc.perform(post("/api/queue-entries/{id}/start", queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.booking.status").value("IN_SERVICE"));
        mockMvc.perform(post("/api/queue-entries/{id}/complete", queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.booking.status").value("COMPLETED"))
                .andExpect(jsonPath("$.estimatedWaitMin").value(0))
                .andExpect(jsonPath("$.completedAt").exists());
        mockMvc.perform(get("/api/bookings/{id}", booking.booking().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void trueCallNextSkipsNonWaitingEntriesAndPreservesPositionsAndWaits() throws Exception {
        CreateQueueEntryRequest first = createQueue(createBookingWithDuration(10, 60), 1, 0);
        CreateQueueEntryRequest second = createQueue(createBookingWithDuration(20, 61), 2, 10);
        CreateQueueEntryRequest third = createQueue(createBookingWithDuration(30, 62), 3, 30);
        postQueueAction(first, "call");
        postQueueAction(first, "start");

        mockMvc.perform(post("/api/queue-entries/call-next")
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueEntryId").value(second.queueEntryId()))
                .andExpect(jsonPath("$.queueStatus").value("CALLED"))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.position").value(2))
                .andExpect(jsonPath("$.estimatedWaitMin").value(10))
                .andExpect(jsonPath("$.calledAt").exists());

        mockMvc.perform(get("/api/queue-entries").with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].queueEntryId").value(first.queueEntryId()))
                .andExpect(jsonPath("$[0].queueStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].estimatedWaitMin").value(0))
                .andExpect(jsonPath("$[1].queueEntryId").value(second.queueEntryId()))
                .andExpect(jsonPath("$[1].queueStatus").value("CALLED"))
                .andExpect(jsonPath("$[1].position").value(2))
                .andExpect(jsonPath("$[1].estimatedWaitMin").value(10))
                .andExpect(jsonPath("$[2].queueEntryId").value(third.queueEntryId()))
                .andExpect(jsonPath("$[2].queueStatus").value("WAITING"))
                .andExpect(jsonPath("$[2].position").value(3))
                .andExpect(jsonPath("$[2].estimatedWaitMin").value(30));
    }

    @Test
    void repeatedTrueCallNextSelectsEachWaitingEntryThenReturnsNotFound() throws Exception {
        CreateQueueEntryRequest first = createQueue(createBookingWithDuration(10, 63), 1, 0);
        CreateQueueEntryRequest second = createQueue(createBookingWithDuration(20, 64), 2, 10);
        CreateQueueEntryRequest third = createQueue(createBookingWithDuration(30, 65), 3, 30);

        for (CreateQueueEntryRequest expected : List.of(first, second, third)) {
            mockMvc.perform(post("/api/queue-entries/call-next")
                            .with(authentication.platformAdminJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.queueEntryId").value(expected.queueEntryId()))
                    .andExpect(jsonPath("$.queueStatus").value("CALLED"));
        }

        mockMvc.perform(post("/api/queue-entries/call-next")
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No waiting queue entry available"))
                .andExpect(jsonPath("$.path").value("/api/queue-entries/call-next"));
    }

    @Test
    void startRejectsIncompatibleCanonicalBookingWithStandardError() throws Exception {
        BookingApiFixture.CreatedBooking booking = bookingFixture().createBooking(TestDates.futureDays(40));
        CreateQueueEntryRequest queue = createQueue(booking);
        postQueueAction(queue, "call");
        Booking canonical = bookingRepository.findById(booking.booking().bookingId()).orElseThrow();
        canonical.setStatus(BookingStatus.CANCELLED);
        bookingRepository.update(canonical);

        mockMvc.perform(post("/api/queue-entries/{id}/start", queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Booking must be confirmed before service can start"))
                .andExpect(jsonPath("$.path").value("/api/queue-entries/" + queue.queueEntryId() + "/start"));
    }

    @Test
    void completionRejectsIncompatibleCanonicalBookingWithStandardError() throws Exception {
        BookingApiFixture.CreatedBooking booking = bookingFixture().createBooking(TestDates.futureDays(41));
        CreateQueueEntryRequest queue = createQueue(booking);
        postQueueAction(queue, "call");
        postQueueAction(queue, "start");
        Booking canonical = bookingRepository.findById(booking.booking().bookingId()).orElseThrow();
        canonical.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.update(canonical);

        mockMvc.perform(post("/api/queue-entries/{id}/complete", queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Booking must be in service before queue completion"))
                .andExpect(jsonPath("$.path").value("/api/queue-entries/" + queue.queueEntryId() + "/complete"));
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
        mockMvc.perform(post("/api/queue-entries/{id}/call", queue.queueEntryId())
                        .with(authentication.platformAdminJwt())).andExpect(status().isOk());
        mockMvc.perform(post("/api/queue-entries/{id}/start", queue.queueEntryId())
                        .with(authentication.platformAdminJwt())).andExpect(status().isOk());
        mockMvc.perform(post("/api/queue-entries/{id}/complete", queue.queueEntryId())
                        .with(authentication.platformAdminJwt())).andExpect(status().isOk());

        mockMvc.perform(post("/api/queue-entries/{id}/call", queue.queueEntryId())
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
        return createQueue(booking, 1, 0);
    }

    private CreateQueueEntryRequest createQueue(BookingApiFixture.CreatedBooking booking,
                                                int expectedPosition, int expectedWait) throws Exception {
        confirmBooking(booking);
        CreateQueueEntryRequest queue = QueueFixtureBuilder.valid(ids, booking.booking().bookingId(),
                booking.resources().service().serviceId()).build();
        api.createQueueEntry(queue)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.queueEntryId").value(queue.queueEntryId()))
                .andExpect(jsonPath("$.queueStatus").value("WAITING"))
                .andExpect(jsonPath("$.position").value(expectedPosition))
                .andExpect(jsonPath("$.estimatedWaitMin").value(expectedWait));
        return queue;
    }

    private BookingApiFixture.CreatedBooking createBookingWithDuration(int duration, int futureDay) throws Exception {
        CreateUserRequest user = UserFixtureBuilder.valid(ids).build();
        api.createUser(user).andExpect(status().isCreated());
        CreateVehicleRequest vehicle = VehicleFixtureBuilder.valid(ids, user.userId()).build();
        api.createVehicle(vehicle).andExpect(status().isCreated());
        CreateServiceRequest service = ServiceFixtureBuilder.valid(ids)
                .estimatedDurationMin(duration)
                .build();
        api.createService(service).andExpect(status().isCreated());
        CreateBookingRequest booking = BookingFixtureBuilder.valid(
                        ids, user.userId(), vehicle.vehicleId(), service.serviceId())
                .scheduledDateTime(TestDates.futureDays(futureDay))
                .build();
        api.createBooking(booking).andExpect(status().isCreated());
        return new BookingApiFixture.CreatedBooking(
                new BookingApiFixture.Resources(user, vehicle, service), booking);
    }

    private void confirmBooking(BookingApiFixture.CreatedBooking booking) throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/confirm", booking.booking().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    private void postQueueAction(CreateQueueEntryRequest queue, String action) throws Exception {
        mockMvc.perform(post("/api/queue-entries/{id}/" + action, queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
    }

    private void assertPositionValidation(String queueEntryId, String body) throws Exception {
        mockMvc.perform(put("/api/queue-entries/{id}/position", queueEntryId)
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'position')]").exists());
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
