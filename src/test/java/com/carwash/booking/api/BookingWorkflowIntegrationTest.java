package com.carwash.booking.api;

import com.carwash.booking.domain.Booking;
import com.carwash.catalog.domain.Service;
import com.carwash.identity.domain.User;
import com.carwash.vehicle.domain.Vehicle;

import com.carwash.booking.api.dto.CreateBookingRequest;
import com.carwash.queue.api.dto.CreateQueueEntryRequest;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.BookingApiFixture;
import com.carwash.testsupport.BookingFixtureBuilder;
import com.carwash.testsupport.QueueFixtureBuilder;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingWorkflowIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void bookingApiCreateConfirmAndInvalid() throws Exception {
        BookingApiFixture fixture = fixture();
        BookingApiFixture.Resources resources = fixture.createResources();
        CreateBookingRequest valid = booking(resources, TestDates.future());
        api.createBooking(valid).andExpect(status().isCreated());
        mockMvc.perform(post("/api/bookings/{id}/confirm", valid.bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        CreateBookingRequest invalid = booking(resources, TestDates.past());
        api.createBooking(invalid).andExpect(status().isBadRequest());
    }

    @Test
    void bookingApiCancelOwnFutureBookingSucceeds() throws Exception {
        BookingApiFixture.CreatedBooking created = fixture().createBooking(TestDates.futureDays(1));
        mockMvc.perform(post("/api/bookings/{id}/cancel", created.booking().bookingId())
                        .with(authentication.platformAdminJwt())
                        .param("customerId", created.resources().user().userId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void bookingApiDeleteCancelWithValidCustomerIdSucceeds() throws Exception {
        BookingApiFixture.CreatedBooking created = fixture().createBooking(TestDates.futureDays(2));
        mockMvc.perform(delete("/api/bookings/{id}", created.booking().bookingId())
                        .with(authentication.platformAdminJwt())
                        .param("customerId", created.resources().user().userId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void queuedBookingCancellationRemovesEntryAndRebalancesRemainingQueue() throws Exception {
        BookingApiFixture.CreatedBooking firstBooking = fixture().createBooking(TestDates.futureDays(30));
        BookingApiFixture.CreatedBooking secondBooking = fixture().createBooking(TestDates.futureDays(31));
        CreateQueueEntryRequest firstQueue = createConfirmedQueue(firstBooking);
        CreateQueueEntryRequest secondQueue = createConfirmedQueue(secondBooking);

        mockMvc.perform(post("/api/bookings/{id}/cancel", firstBooking.booking().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/queue-entries/{id}", firstQueue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/queue-entries/{id}", secondQueue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.estimatedWaitMin").value(0));
    }

    @Test
    void inServiceBookingCancellationReturnsStandardBusinessRuleError() throws Exception {
        BookingApiFixture.CreatedBooking booking = fixture().createBooking(TestDates.futureDays(32));
        CreateQueueEntryRequest queue = createConfirmedQueue(booking);
        postQueueAction(queue, "call");
        postQueueAction(queue, "start");

        mockMvc.perform(post("/api/bookings/{id}/cancel", booking.booking().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Booking cannot be cancelled while service is in progress"))
                .andExpect(jsonPath("$.path").value("/api/bookings/" + booking.booking().bookingId() + "/cancel"));
    }

    @Test
    void activeQueuedBookingUpdateReturnsStandardBusinessRuleError() throws Exception {
        BookingApiFixture.CreatedBooking booking = fixture().createBooking(TestDates.futureDays(33));
        createConfirmedQueue(booking);
        String request = objectMapper.writeValueAsString(java.util.Map.of(
                "vehicleId", booking.resources().vehicle().vehicleId(),
                "serviceOfferingId", booking.resources().offering().offeringId(),
                "specialRequest", "must not change"
        ));

        mockMvc.perform(put("/api/bookings/{id}", booking.booking().bookingId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(
                        "Booking cannot be updated while it has an active queue entry"))
                .andExpect(jsonPath("$.path").value("/api/bookings/" + booking.booking().bookingId()));
    }

    @Test
    void bookingApiCancelWithoutCustomerIdSucceedsForAuthorizedCaller() throws Exception {
        BookingApiFixture.CreatedBooking created = fixture().createBooking(TestDates.futureDays(3));
        mockMvc.perform(post("/api/bookings/{id}/cancel", created.booking().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void bookingApiCancelIgnoresBlankLegacyCustomerIdForAuthorizedCaller() throws Exception {
        BookingApiFixture.CreatedBooking created = fixture().createBooking(TestDates.futureDays(4));
        mockMvc.perform(post("/api/bookings/{id}/cancel", created.booking().bookingId())
                        .with(authentication.platformAdminJwt())
                        .param("customerId", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void bookingApiCancelDoesNotTrustLegacyCustomerIdForAuthorizedAdmin() throws Exception {
        BookingApiFixture.CreatedBooking created = fixture().createBooking(TestDates.futureDays(5));
        mockMvc.perform(post("/api/bookings/{id}/cancel", created.booking().bookingId())
                        .with(authentication.platformAdminJwt())
                        .param("customerId", ids.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void createBookingRejectsUnknownUser() throws Exception {
        BookingApiFixture.Resources resources = fixture().createResources();
        CreateBookingRequest request = BookingFixtureBuilder.valid(ids, ids.user(),
                        resources.vehicle().vehicleId(), resources.branch().branchId(),
                        resources.offering().offeringId())
                .scheduledDateTime(TestDates.futureDays(6)).build();
        api.createBooking(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("User not found")))
                .andExpect(jsonPath("$.path").value("/api/bookings"));
    }

    @Test
    void createBookingRejectsUnknownVehicle() throws Exception {
        BookingApiFixture.Resources resources = fixture().createResources();
        CreateBookingRequest request = BookingFixtureBuilder.valid(ids, resources.user().userId(), ids.vehicle(),
                        resources.branch().branchId(), resources.offering().offeringId())
                .scheduledDateTime(TestDates.futureDays(7)).build();
        api.createBooking(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Vehicle not found")))
                .andExpect(jsonPath("$.path").value("/api/bookings"));
    }

    @Test
    void createBookingRejectsUnknownOffering() throws Exception {
        BookingApiFixture.Resources resources = fixture().createResources();
        CreateBookingRequest request = BookingFixtureBuilder.valid(ids, resources.user().userId(),
                        resources.vehicle().vehicleId(), resources.branch().branchId(), ids.offering())
                .scheduledDateTime(TestDates.futureDays(8)).build();
        api.createBooking(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Offering not found")))
                .andExpect(jsonPath("$.path").value("/api/bookings"));
    }

    @Test
    void createBookingRejectsInactiveService() throws Exception {
        BookingApiFixture.Resources resources = fixture().createResources();
        api.deactivateService(resources.service().serviceId()).andExpect(status().isOk());
        CreateBookingRequest request = booking(resources, TestDates.futureDays(9));
        api.createBooking(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Inactive service")))
                .andExpect(jsonPath("$.path").value("/api/bookings"));
    }

    @Test
    void createBookingRejectsVehicleOwnedByDifferentUser() throws Exception {
        BookingApiFixture.Resources first = fixture().createResources();
        BookingApiFixture.Resources second = fixture().createResources();
        CreateBookingRequest request = BookingFixtureBuilder.valid(ids, first.user().userId(),
                        second.vehicle().vehicleId(), first.branch().branchId(), first.offering().offeringId())
                .scheduledDateTime(TestDates.futureDays(10)).build();
        api.createBooking(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Vehicle does not belong")))
                .andExpect(jsonPath("$.path").value("/api/bookings"));
    }

    @Test
    void createBookingRejectsFullTimeSlot() throws Exception {
        LocalDateTime scheduled = TestDates.futureDays(11);
        BookingApiFixture.CreatedBooking first = fixture().createBooking(scheduled);
        BookingApiFixture.Resources second = fixture().createResources();
        CreateBookingRequest request = BookingFixtureBuilder.valid(
                        ids, second.user().userId(), second.vehicle().vehicleId(),
                        first.resources().branch().branchId(), first.resources().offering().offeringId())
                .scheduledDateTime(scheduled).build();
        api.createBooking(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("time slot")))
                .andExpect(jsonPath("$.path").value("/api/bookings"));
    }

    @Test
    void createBookingRejectsSameVehicleCustomerConflict() throws Exception {
        LocalDateTime scheduled = TestDates.futureDays(12);
        BookingApiFixture.CreatedBooking existing = fixture().createBooking(scheduled);
        CreateBookingRequest conflicting = BookingFixtureBuilder.valid(ids,
                        existing.resources().user().userId(), existing.resources().vehicle().vehicleId(),
                        existing.resources().branch().branchId(), existing.resources().offering().offeringId())
                .scheduledDateTime(scheduled).build();
        api.createBooking(conflicting)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Customer vehicle")))
                .andExpect(jsonPath("$.path").value("/api/bookings"));
    }

    @Test
    void createBookingAllowsBookingAfterPreviousBookingWasCancelled() throws Exception {
        LocalDateTime scheduled = TestDates.futureDays(13);
        BookingApiFixture.CreatedBooking first = fixture().createBooking(scheduled);
        mockMvc.perform(post("/api/bookings/{id}/cancel", first.booking().bookingId())
                        .with(authentication.platformAdminJwt())
                        .param("customerId", first.resources().user().userId()))
                .andExpect(status().isOk());
        BookingApiFixture.Resources second = fixture().createResources();
        CreateBookingRequest replacement = booking(second, scheduled);
        api.createBooking(replacement)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(replacement.bookingId()));
    }

    private BookingApiFixture fixture() {
        return new BookingApiFixture(api, ids);
    }

    private CreateBookingRequest booking(BookingApiFixture.Resources resources, LocalDateTime scheduled) {
        return BookingFixtureBuilder.valid(ids, resources.user().userId(), resources.vehicle().vehicleId(),
                        resources.branch().branchId(), resources.offering().offeringId())
                .scheduledDateTime(scheduled)
                .build();
    }

    private CreateQueueEntryRequest createConfirmedQueue(BookingApiFixture.CreatedBooking booking) throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/confirm", booking.booking().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        CreateQueueEntryRequest queue = QueueFixtureBuilder.valid(ids, booking.booking().bookingId(),
                booking.resources().service().serviceId()).build();
        api.createQueueEntry(queue).andExpect(status().isCreated());
        return queue;
    }

    private void postQueueAction(CreateQueueEntryRequest queue, String action) throws Exception {
        mockMvc.perform(post("/api/queue-entries/{id}/" + action, queue.queueEntryId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
    }
}
