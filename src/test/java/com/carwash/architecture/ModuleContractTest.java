package com.carwash.architecture;

import com.carwash.booking.application.BookingQuery;
import com.carwash.booking.application.BranchAvailabilityCandidateQuery;
import com.carwash.booking.domain.Booking;
import com.carwash.catalog.domain.Service;
import com.carwash.catalog.application.ServiceDefinitionQuery;
import com.carwash.catalog.application.ServiceOfferingQuery;
import com.carwash.discovery.application.DistanceCalculator;
import com.carwash.discovery.application.NearbyBranchDiscoveryService;
import com.carwash.identity.application.UserQuery;
import com.carwash.identity.domain.User;
import com.carwash.queue.application.QueueQuery;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.marketplace.application.BranchScheduleQuery;
import com.carwash.marketplace.application.MarketplaceQuery;
import com.carwash.testsupport.ServiceTestSupport;
import com.carwash.vehicle.application.VehicleQuery;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.recommendation.application.RecommendationMetricProvider;
import com.carwash.recommendation.application.RecommendationProperties;
import com.carwash.recommendation.application.RecommendationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

class ModuleContractTest extends ServiceTestSupport {

    @Test
    void published_queries_resolve_canonical_ownership_without_exposing_infrastructure() {
        User user = registerUser();
        Vehicle vehicle = createVehicle(user);
        Service service = createService();
        String offeringId = createOffering(service);
        Booking booking = bookingService.createBooking(
                ids.booking(), user.getUserId(), vehicle.getVehicleId(), ensureDefaultBranch(), offeringId,
                com.carwash.testsupport.TestDates.future(), "none");
        bookingService.confirmBooking(booking.getBookingId());
        QueueEntry queueEntry = queueService.createQueueEntry(
                ids.queueEntry(), booking.getBookingId(), service.getServiceId());

        UserQuery users = userService;
        VehicleQuery vehicles = vehicleService;
        BookingQuery bookings = bookingService;
        QueueQuery queues = queueService;

        assertEquals(user.getUserId(), users.findOptionalById(user.getUserId()).orElseThrow().getUserId());
        assertEquals(user.getUserId(), vehicles.findOwnerId(vehicle.getVehicleId()).orElseThrow());
        assertEquals(user.getUserId(), bookings.findOwnerId(booking.getBookingId()).orElseThrow());
        assertEquals(user.getUserId(), queues.findOwnerId(queueEntry.getQueueEntryId()).orElseThrow());
        assertTrue(bookings.existsByUserId(user.getUserId()));
        assertTrue(bookings.existsByVehicleId(vehicle.getVehicleId()));
        assertTrue(bookings.existsByServiceId(service.getServiceId()));
        assertTrue(queues.existsByServiceId(service.getServiceId()));
    }

    @Test
    void reporting_queries_publish_the_existing_booking_and_detached_queue_views() {
        Booking booking = createConfirmedBooking();
        QueueEntry queueEntry = queueService.createQueueEntry(
                ids.queueEntry(), booking.getBookingId(), booking.getService().getServiceId());

        assertTrue(((BookingQuery) bookingService).findBookingSnapshots().stream()
                .anyMatch(item -> booking.getBookingId().equals(item.bookingId())));
        var publishedQueueEntry = ((QueueQuery) queueService).findQueueEntrySnapshots().stream()
                .filter(item -> queueEntry.getQueueEntryId().equals(item.queueEntryId()))
                .findFirst()
                .orElseThrow();
        assertEquals(booking.getBookingId(), publishedQueueEntry.bookingId());
    }

    @Test
    void discovery_constructor_depends_only_on_published_queries_and_distance_port() {
        Set<Class<?>> dependencies = Set.of(
                NearbyBranchDiscoveryService.class.getConstructors()[0].getParameterTypes());

        assertEquals(Set.of(
                MarketplaceQuery.class,
                BranchScheduleQuery.class,
                ServiceOfferingQuery.class,
                ServiceDefinitionQuery.class,
                DistanceCalculator.class), dependencies);
    }

    @Test
    void recommendation_constructor_uses_booking_candidate_contract_configuration_and_metric_ports() {
        Set<Class<?>> dependencies = Set.of(
                RecommendationService.class.getConstructors()[0].getParameterTypes());

        assertEquals(Set.of(
                BranchAvailabilityCandidateQuery.class,
                RecommendationProperties.class,
                java.util.List.class), dependencies);
        assertTrue(RecommendationMetricProvider.class.isInterface());
    }
}
