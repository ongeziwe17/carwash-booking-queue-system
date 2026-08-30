package com.carwash.booking.api;

import com.carwash.booking.domain.Booking;

import com.carwash.booking.api.dto.CreateBookingRequest;
import com.carwash.queue.api.dto.CreateQueueEntryRequest;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.BookingApiFixture;
import com.carwash.testsupport.BookingFixtureBuilder;
import com.carwash.testsupport.QueueFixtureBuilder;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingReschedulingIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void createdBookingReschedulePersistsNewScheduleAndNotification() throws Exception {
        BookingApiFixture.CreatedBooking created = fixture().createBooking(TestDates.futureDays(80));
        LocalDateTime target = TestDates.futureDays(81);

        reschedule(created.booking().bookingId(), target)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDateTime").value(apiDateTime(target)))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.user.userId").value(created.resources().user().userId()))
                .andExpect(jsonPath("$.vehicle.vehicleId").value(created.resources().vehicle().vehicleId()))
                .andExpect(jsonPath("$.service.serviceId").value(created.resources().service().serviceId()));
        mockMvc.perform(get("/api/bookings/{id}", created.booking().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDateTime").value(apiDateTime(target)))
                .andExpect(jsonPath("$.status").value("CREATED"));
        mockMvc.perform(get("/api/notifications/user/{userId}", created.resources().user().userId())
                        .with(authentication.platformAdminJwt())
                        .param("businessId", created.resources().business().businessId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("BOOKING_RESCHEDULED"))
                .andExpect(jsonPath("$[0].bookingId").value(created.booking().bookingId()))
                .andExpect(jsonPath("$[0].branchId").value(created.resources().branch().branchId()))
                .andExpect(jsonPath("$[0].serviceOfferingId").value(created.resources().offering().offeringId()))
                .andExpect(jsonPath("$[0].message").value(containsString(target.toString())));
    }

    @Test
    void confirmedBookingReschedulePreservesStatus() throws Exception {
        BookingApiFixture.CreatedBooking created = fixture().createBooking(TestDates.futureDays(82));
        confirm(created.booking().bookingId());
        LocalDateTime target = TestDates.futureDays(83);

        reschedule(created.booking().bookingId(), target)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDateTime").value(apiDateTime(target)))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void genericPutPreservesScheduleAndRejectsOldScheduleField() throws Exception {
        BookingApiFixture.CreatedBooking created = fixture().createBooking(TestDates.futureDays(84));
        LocalDateTime original = created.booking().scheduledDateTime();
        Map<String, Object> validUpdate = Map.of(
                "vehicleId", created.resources().vehicle().vehicleId(),
                "serviceOfferingId", created.resources().offering().offeringId(),
                "specialRequest", "non-schedule update"
        );

        mockMvc.perform(put("/api/bookings/{id}", created.booking().bookingId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDateTime").value(apiDateTime(original)))
                .andExpect(jsonPath("$.specialRequest").value("non-schedule update"));

        Map<String, Object> bypass = new java.util.LinkedHashMap<>(validUpdate);
        bypass.put("scheduledDateTime", TestDates.futureDays(85).toString());
        mockMvc.perform(put("/api/bookings/{id}", created.booking().bookingId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bypass)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(get("/api/bookings/{id}", created.booking().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDateTime").value(apiDateTime(original)));
    }

    @Test
    void rescheduleValidationAndUnknownBookingUseStandardErrors() throws Exception {
        BookingApiFixture.CreatedBooking created = fixture().createBooking(TestDates.futureDays(86));

        mockMvc.perform(post("/api/bookings/{id}/reschedule", created.booking().bookingId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'scheduledDateTime')]").exists());
        reschedule(created.booking().bookingId(), TestDates.past())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Scheduled date/time must be in the future"));
        reschedule("missing-booking", TestDates.futureDays(87))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/bookings/missing-booking/reschedule"));
    }

    @Test
    void rescheduleRejectsFullSlotAndCustomerVehicleConflictWithoutMutation() throws Exception {
        LocalDateTime fullTarget = TestDates.futureDays(88);
        BookingApiFixture.CreatedBooking moving = fixture().createBooking(TestDates.futureDays(89));
        BookingApiFixture.Resources otherCustomer = fixture().createResources();
        api.createBooking(BookingFixtureBuilder.valid(
                        ids, otherCustomer.user().userId(), otherCustomer.vehicle().vehicleId(),
                        moving.resources().branch().branchId(), moving.resources().offering().offeringId())
                .scheduledDateTime(fullTarget).build()).andExpect(status().isCreated());

        reschedule(moving.booking().bookingId(), fullTarget)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Booking time slot is already full"));
        assertSchedule(moving.booking().bookingId(), moving.booking().scheduledDateTime());

        BookingApiFixture.Resources resources = fixture().createResources();
        CreateBookingRequest conflictMoving = createBooking(resources, TestDates.futureDays(90));
        createBooking(resources, TestDates.futureDays(91));
        reschedule(conflictMoving.bookingId(), TestDates.futureDays(91))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Customer vehicle")));
        assertSchedule(conflictMoving.bookingId(), conflictMoving.scheduledDateTime());
    }

    @Test
    void rescheduleRejectsInactiveServiceAndActiveQueueStates() throws Exception {
        BookingApiFixture.CreatedBooking inactive = fixture().createBooking(TestDates.futureDays(92));
        api.deactivateService(inactive.resources().service().serviceId()).andExpect(status().isOk());
        reschedule(inactive.booking().bookingId(), TestDates.futureDays(93))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Inactive service offering or reusable service cannot be booked"));

        BookingApiFixture.CreatedBooking waiting = fixture().createBooking(TestDates.futureDays(94));
        CreateQueueEntryRequest waitingQueue = createConfirmedQueue(waiting);
        reschedule(waiting.booking().bookingId(), TestDates.futureDays(95))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Booking cannot be rescheduled while it has an active queue entry"));
        mockMvc.perform(get("/api/queue-entries/{id}", waitingQueue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueStatus").value("WAITING"))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.estimatedWaitMin").value(0));

        BookingApiFixture.CreatedBooking called = fixture().createBooking(TestDates.futureDays(96));
        CreateQueueEntryRequest calledQueue = createConfirmedQueue(called);
        queueAction(calledQueue.queueEntryId(), "call");
        reschedule(called.booking().bookingId(), TestDates.futureDays(97))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Booking cannot be rescheduled while it has an active queue entry"));
    }

    @Test
    void rescheduleRejectsInServiceCompletedAndCancelledBookings() throws Exception {
        BookingApiFixture.CreatedBooking serviceWork = fixture().createBooking(TestDates.futureDays(98));
        CreateQueueEntryRequest queue = createConfirmedQueue(serviceWork);
        queueAction(queue.queueEntryId(), "call");
        queueAction(queue.queueEntryId(), "start");
        reschedule(serviceWork.booking().bookingId(), TestDates.futureDays(99))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Booking cannot be rescheduled in its current state"));

        queueAction(queue.queueEntryId(), "complete");
        reschedule(serviceWork.booking().bookingId(), TestDates.futureDays(100))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Booking cannot be rescheduled in its current state"));

        BookingApiFixture.CreatedBooking cancelled = fixture().createBooking(TestDates.futureDays(101));
        mockMvc.perform(post("/api/bookings/{id}/cancel", cancelled.booking().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        reschedule(cancelled.booking().bookingId(), TestDates.futureDays(102))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Booking cannot be rescheduled in its current state"));
    }

    private BookingApiFixture fixture() {
        return new BookingApiFixture(api, ids);
    }

    private CreateBookingRequest createBooking(BookingApiFixture.Resources resources, LocalDateTime schedule)
            throws Exception {
        CreateBookingRequest booking = BookingFixtureBuilder.valid(
                        ids, resources.user().userId(), resources.vehicle().vehicleId(),
                        resources.branch().branchId(), resources.offering().offeringId())
                .scheduledDateTime(schedule)
                .build();
        api.createBooking(booking).andExpect(status().isCreated());
        return booking;
    }

    private CreateQueueEntryRequest createConfirmedQueue(BookingApiFixture.CreatedBooking booking) throws Exception {
        confirm(booking.booking().bookingId());
        CreateQueueEntryRequest queue = QueueFixtureBuilder.valid(
                ids, booking.booking().bookingId(), booking.resources().service().serviceId()).build();
        api.createQueueEntry(queue).andExpect(status().isCreated());
        return queue;
    }

    private void confirm(String bookingId) throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/confirm", bookingId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
    }

    private void queueAction(String queueEntryId, String action) throws Exception {
        mockMvc.perform(post("/api/queue-entries/{id}/" + action, queueEntryId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
    }

    private ResultActions reschedule(String bookingId, LocalDateTime scheduledDateTime) throws Exception {
        return mockMvc.perform(post("/api/bookings/{id}/reschedule", bookingId)
                .with(authentication.platformAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "scheduledDateTime", scheduledDateTime.toString()))));
    }

    private void assertSchedule(String bookingId, LocalDateTime expected) throws Exception {
        mockMvc.perform(get("/api/bookings/{id}", bookingId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDateTime").value(apiDateTime(expected)));
    }

    private String apiDateTime(LocalDateTime value) {
        return value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }
}
