package com.carwash.api;

import com.carwash.booking.api.dto.CreateBookingRequest;
import com.carwash.catalog.api.dto.CreateServiceRequest;
import com.carwash.identity.api.dto.CreateUserRequest;
import com.carwash.vehicle.api.dto.CreateVehicleRequest;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.notification.domain.NotificationRepository;
import com.carwash.queue.domain.QueueEntryRepository;
import com.carwash.catalog.domain.ServiceRepository;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.BookingApiFixture;
import com.carwash.testsupport.BookingFixtureBuilder;
import com.carwash.testsupport.ServiceFixtureBuilder;
import com.carwash.testsupport.UserFixtureBuilder;
import com.carwash.testsupport.VehicleFixtureBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(AvailabilityWorkflowIntegrationTest.FixedAvailabilityClockConfiguration.class)
@TestPropertySource(properties = "carwash.policy.booking.max-active-bookings-per-slot=2")
class AvailabilityWorkflowIntegrationTest extends ApiIntegrationTestSupport {

    private static final LocalDate TODAY = LocalDate.of(2090, 1, 15);
    private static final LocalDate FUTURE_DATE = TODAY.plusDays(1);

    @Autowired BookingRepository bookings;
    @Autowired ServiceRepository services;
    @Autowired QueueEntryRepository queues;
    @Autowired NotificationRepository notifications;

    @Test
    void activeServiceWithoutBookingsReturnsDeterministicBoundedSlotsAndDoesNotMutateState() throws Exception {
        CreateServiceRequest service = createService(30);
        int bookingCount = bookings.findAll().size();
        int serviceCount = services.findAll().size();
        int queueCount = queues.findAll().size();
        int notificationCount = notifications.findAll().size();

        String responseBody = availability(service.serviceId(), FUTURE_DATE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(service.serviceId()))
                .andExpect(jsonPath("$.serviceName").value(service.serviceName()))
                .andExpect(jsonPath("$.date").value(FUTURE_DATE.toString()))
                .andExpect(jsonPath("$.estimatedServiceDurationMin").value(30))
                .andExpect(jsonPath("$.slotCapacity").value(2))
                .andExpect(jsonPath("$.slots.length()").value(18))
                .andExpect(jsonPath("$.slots[0].startDateTime").value(FUTURE_DATE + "T08:00:00"))
                .andExpect(jsonPath("$.slots[0].estimatedEndDateTime").value(FUTURE_DATE + "T08:30:00"))
                .andExpect(jsonPath("$.slots[0].capacityRemaining").value(2))
                .andReturn().getResponse().getContentAsString();

        JsonNode slots = objectMapper.readTree(responseBody).path("slots");
        List<LocalDateTime> starts = new ArrayList<>();
        slots.forEach(slot -> starts.add(LocalDateTime.parse(slot.path("startDateTime").asText())));
        assertEquals(starts.stream().sorted().toList(), starts);
        assertEquals(bookingCount, bookings.findAll().size());
        assertEquals(serviceCount, services.findAll().size());
        assertEquals(queueCount, queues.findAll().size());
        assertEquals(notificationCount, notifications.findAll().size());
        assertTrue(services.findById(service.serviceId()).orElseThrow().isActive());
        assertEquals(30, services.findById(service.serviceId()).orElseThrow().getEstimatedDurationMin());
    }

    @Test
    void partialAndFullGlobalCapacityStayConsistentWithBookingAndCancellation() throws Exception {
        CreateServiceRequest requestedService = createService(30);
        CreateServiceRequest otherService = createService(30);
        LocalDateTime target = FUTURE_DATE.atTime(9, 0);
        CreateBookingRequest first = createBooking(otherService.serviceId(), target);

        availability(requestedService.serviceId(), FUTURE_DATE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[?(@.startDateTime == '" + target + ":00')].capacityRemaining")
                        .value(org.hamcrest.Matchers.contains(1)));

        CreateBookingRequest second = createBooking(requestedService.serviceId(), target);
        availability(requestedService.serviceId(), FUTURE_DATE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[?(@.startDateTime == '" + target + ":00')]").isEmpty());

        CreateBookingRequest rejected = bookingRequest(requestedService.serviceId(), target);
        api.createBooking(rejected)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Booking time slot is already full"));

        cancel(second.bookingId());
        availability(requestedService.serviceId(), FUTURE_DATE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[?(@.startDateTime == '" + target + ":00')].capacityRemaining")
                        .value(org.hamcrest.Matchers.contains(1)));
        cancel(first.bookingId());
        availability(requestedService.serviceId(), FUTURE_DATE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[?(@.startDateTime == '" + target + ":00')].capacityRemaining")
                        .value(org.hamcrest.Matchers.contains(2)));
    }

    @Test
    void advertisedStartCanBeSubmittedToAuthoritativeBookingCreation() throws Exception {
        CreateServiceRequest service = createService(60);
        String body = availability(service.serviceId(), FUTURE_DATE)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        LocalDateTime advertised = LocalDateTime.parse(
                objectMapper.readTree(body).path("slots").get(0).path("startDateTime").asText());

        CreateBookingRequest booking = bookingRequest(service.serviceId(), advertised);
        api.createBooking(booking)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduledDateTime").value(advertised + ":00"));
    }

    @Test
    void todayOmitsPastAndCurrentSlotsAndDurationTruncatesClosingTail() throws Exception {
        CreateServiceRequest service = createService(60);
        String body = availability(service.serviceId(), TODAY)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode slots = objectMapper.readTree(body).path("slots");
        assertFalse(slots.isEmpty());
        assertEquals(TODAY + "T10:30:00", slots.get(0).path("startDateTime").asText());
        assertEquals(TODAY + "T16:00:00", slots.get(slots.size() - 1).path("startDateTime").asText());
        slots.forEach(slot -> assertTrue(LocalDateTime.parse(slot.path("startDateTime").asText())
                .isAfter(LocalDateTime.of(TODAY, java.time.LocalTime.of(10, 10)))));
    }

    @Test
    void requestValidationUsesStandardContract() throws Exception {
        CreateServiceRequest service = createService(30);

        assertValidation(availability(null, FUTURE_DATE), "serviceId");
        assertValidation(mockMvc.perform(get("/api/availability")
                .with(authentication.platformAdminJwt()).param("serviceId", " ")
                .param("date", FUTURE_DATE.toString())), "serviceId");
        assertValidation(mockMvc.perform(get("/api/availability")
                .with(authentication.platformAdminJwt()).param("serviceId", "x".repeat(65))
                .param("date", FUTURE_DATE.toString())), "serviceId");
        assertValidation(mockMvc.perform(get("/api/availability")
                .with(authentication.platformAdminJwt()).param("serviceId", service.serviceId())), "date");
        assertValidation(availability(service.serviceId(), TODAY.minusDays(1)), "date");
    }

    @Test
    void unknownInactiveAndUnauthenticatedRequestsUseStandardErrors() throws Exception {
        availability("missing-service", FUTURE_DATE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        CreateServiceRequest inactive = createService(30);
        api.deactivateService(inactive.serviceId()).andExpect(status().isOk());
        availability(inactive.serviceId(), FUTURE_DATE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Inactive service cannot be booked"));

        mockMvc.perform(get("/api/availability")
                        .param("serviceId", inactive.serviceId()).param("date", FUTURE_DATE.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void reschedulingRejectsGridWindowAndDurationViolationsWithoutMovingBooking() throws Exception {
        BookingApiFixture.CreatedBooking created = new BookingApiFixture(api, ids)
                .createBooking(FUTURE_DATE.atTime(9, 0));
        String bookingId = created.booking().bookingId();
        String serviceId = created.resources().service().serviceId();
        LocalDateTime original = created.booking().scheduledDateTime();

        assertRejectedReschedule(bookingId, FUTURE_DATE.atTime(9, 10));
        assertRejectedReschedule(bookingId, FUTURE_DATE.atTime(7, 30));

        mockMvc.perform(put("/api/services/{id}", serviceId)
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "serviceName", "Long integration wash",
                                "description", "Duration compatibility fixture",
                                "price", 150,
                                "estimatedDurationMin", 60))))
                .andExpect(status().isOk());
        assertRejectedReschedule(bookingId, FUTURE_DATE.atTime(16, 30));

        mockMvc.perform(get("/api/bookings/{id}", bookingId).with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDateTime").value(original + ":00"));
        mockMvc.perform(post("/api/bookings/{id}/reschedule", bookingId)
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("scheduledDateTime", FUTURE_DATE.atTime(15, 30)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDateTime").value(FUTURE_DATE + "T15:30:00"));
    }

    @Test
    void genericServiceUpdateRejectsAnIncompatibleLongerServiceWithoutPartialMutation() throws Exception {
        BookingApiFixture.CreatedBooking created = new BookingApiFixture(api, ids)
                .createBooking(FUTURE_DATE.atTime(16, 30));
        CreateServiceRequest longService = createService(60);

        mockMvc.perform(put("/api/bookings/{id}", created.booking().bookingId())
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vehicleId", created.resources().vehicle().vehicleId(),
                                "serviceId", longService.serviceId(),
                                "specialRequest", "must not persist"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

        mockMvc.perform(get("/api/bookings/{id}", created.booking().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDateTime").value(FUTURE_DATE + "T16:30:00"))
                .andExpect(jsonPath("$.service.serviceId").value(created.resources().service().serviceId()))
                .andExpect(jsonPath("$.specialRequest").value(created.booking().specialRequest()));
    }

    private ResultActions availability(String serviceId, LocalDate date) throws Exception {
        var request = get("/api/availability").with(authentication.platformAdminJwt());
        if (serviceId != null) request.param("serviceId", serviceId);
        if (date != null) request.param("date", date.toString());
        return mockMvc.perform(request);
    }

    private CreateServiceRequest createService(int durationMinutes) throws Exception {
        CreateServiceRequest request = ServiceFixtureBuilder.valid(ids)
                .estimatedDurationMin(durationMinutes).build();
        api.createService(request).andExpect(status().isCreated());
        return request;
    }

    private CreateBookingRequest createBooking(String serviceId, LocalDateTime start) throws Exception {
        CreateBookingRequest request = bookingRequest(serviceId, start);
        api.createBooking(request).andExpect(status().isCreated());
        return request;
    }

    private CreateBookingRequest bookingRequest(String serviceId, LocalDateTime start) throws Exception {
        CreateUserRequest user = UserFixtureBuilder.valid(ids).build();
        api.createUser(user).andExpect(status().isCreated());
        CreateVehicleRequest vehicle = VehicleFixtureBuilder.valid(ids, user.userId()).build();
        api.createVehicle(vehicle).andExpect(status().isCreated());
        return BookingFixtureBuilder.valid(ids, user.userId(), vehicle.vehicleId(), serviceId)
                .scheduledDateTime(start).build();
    }

    private void cancel(String bookingId) throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId)
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
    }

    private void assertRejectedReschedule(String bookingId, LocalDateTime target) throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/reschedule", bookingId)
                        .with(authentication.platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("scheduledDateTime", target))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    private void assertValidation(ResultActions action, String field) throws Exception {
        action.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == '" + field + "')]").exists());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedAvailabilityClockConfiguration {
        @Bean
        @Primary
        Clock fixedAvailabilityClock() {
            return Clock.fixed(Instant.parse("2090-01-15T10:10:00Z"), ZoneOffset.UTC);
        }
    }
}
