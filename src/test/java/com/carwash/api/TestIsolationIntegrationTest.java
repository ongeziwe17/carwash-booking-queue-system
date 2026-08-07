package com.carwash.api;

import com.carwash.repository.BookingRepository;
import com.carwash.repository.NotificationRepository;
import com.carwash.repository.QueueEntryRepository;
import com.carwash.repository.ServiceRepository;
import com.carwash.repository.UserRepository;
import com.carwash.repository.VehicleRepository;
import com.carwash.testsupport.ApiIntegrationTestSupport;
import com.carwash.testsupport.BookingApiFixture;
import com.carwash.testsupport.TestDates;
import com.carwash.testsupport.UserFixtureBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TestIsolationIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired UserRepository users;
    @Autowired VehicleRepository vehicles;
    @Autowired ServiceRepository services;
    @Autowired BookingRepository bookings;
    @Autowired QueueEntryRepository queues;
    @Autowired NotificationRepository notifications;

    @Test
    void repositoriesAreEmptyAtStartOfEveryIntegrationTest() {
        assertRepositoriesEmpty();
    }

    @Test
    void dataCreatedByThisMethodCannotBeRequiredByAnotherMethod() throws Exception {
        assertRepositoriesEmpty();
        api.createUser(UserFixtureBuilder.valid(ids).build()).andExpect(status().isCreated());
        assertEquals(1, users.findAll().size());
    }

    @Test
    void anotherMethodStillStartsEmptyRegardlessOfRandomExecutionOrder() {
        assertRepositoriesEmpty();
    }

    @Test
    void notificationIdsBeginFromDeterministicTestState() throws Exception {
        BookingApiFixture.CreatedBooking booking = new BookingApiFixture(api, ids).createBooking(TestDates.futureDays(70));
        mockMvc.perform(post("/api/bookings/{id}/confirm", booking.booking().bookingId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/notifications/user/{userId}", booking.resources().user().userId())
                        .with(authentication.platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notificationId").value("notification-00000000000000000001"));
    }

    private void assertRepositoriesEmpty() {
        assertTrue(users.findAll().isEmpty());
        assertTrue(vehicles.findAll().isEmpty());
        assertTrue(services.findAll().isEmpty());
        assertTrue(bookings.findAll().isEmpty());
        assertTrue(queues.findAll().isEmpty());
        assertTrue(notifications.findAll().isEmpty());
    }
}
