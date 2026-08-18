package com.carwash.booking.application;

import com.carwash.testsupport.ServiceTestSupport;

import com.carwash.booking.application.AvailabilityService;
import com.carwash.booking.application.BookingManagementService;
import com.carwash.booking.application.BookingSlotPolicyService;

import com.carwash.booking.api.dto.ServiceAvailabilityResponse;
import com.carwash.booking.application.BookingPolicyProperties;
import com.carwash.booking.domain.Booking;
import com.carwash.catalog.domain.Service;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilityServiceTest extends ServiceTestSupport {

    @Test
    void noBookingsReturnsEveryValidSlotAtFullCapacity() {
        Service service = createService();

        ServiceAvailabilityResponse response = availabilityService.findAvailability(
                service.getServiceId(), TestDates.future().toLocalDate());

        assertEquals(18, response.slots().size());
        assertEquals(1, response.slotCapacity());
        assertTrue(response.slots().stream().allMatch(slot -> slot.capacityRemaining() == 1));
        assertTrue(response.slots().stream().allMatch(slot ->
                slot.estimatedEndDateTime().equals(slot.startDateTime().plusMinutes(30))));
        assertEquals(response.slots().stream().map(slot -> slot.startDateTime()).sorted().toList(),
                response.slots().stream().map(slot -> slot.startDateTime()).toList());
    }

    @Test
    void partialCapacityRemainsVisibleAndFullCapacityIsOmitted() {
        BookingPolicyProperties capacityTwo = new BookingPolicyProperties(2, Duration.ZERO);
        BookingSlotPolicyService slots = new BookingSlotPolicyService(bookingRepository, capacityTwo, clock);
        BookingManagementService bookings = bookingService(capacityTwo, slots);
        AvailabilityService availability = availabilityService(capacityTwo, slots);
        LocalDateTime target = TestDates.future();

        Booking first = bookings.createBooking(newBookingWithFixture(target));
        assertEquals(1, availability.findAvailability(first.getService().getServiceId(), target.toLocalDate())
                .slots().stream().filter(slot -> slot.startDateTime().equals(target)).findFirst().orElseThrow()
                .capacityRemaining());

        bookings.createBooking(newBookingWithFixture(target));
        assertFalse(availability.findAvailability(first.getService().getServiceId(), target.toLocalDate())
                .slots().stream().anyMatch(slot -> slot.startDateTime().equals(target)));
    }

    @Test
    void cancelledBookingImmediatelyReleasesCapacity() {
        LocalDateTime target = TestDates.futureDays(2);
        Booking booking = bookingService.createBooking(newBookingWithFixture(target));
        assertFalse(availabilityService.findAvailability(booking.getService().getServiceId(), target.toLocalDate())
                .slots().stream().anyMatch(slot -> slot.startDateTime().equals(target)));

        bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId());

        assertEquals(1, availabilityService.findAvailability(booking.getService().getServiceId(), target.toLocalDate())
                .slots().stream().filter(slot -> slot.startDateTime().equals(target)).findFirst().orElseThrow()
                .capacityRemaining());
    }

    @Test
    void capacityIsGlobalAcrossDifferentServices() {
        LocalDateTime target = TestDates.futureDays(3);
        bookingService.createBooking(newBookingWithFixture(target));
        Service requestedService = createService();

        assertFalse(availabilityService.findAvailability(requestedService.getServiceId(), target.toLocalDate())
                .slots().stream().anyMatch(slot -> slot.startDateTime().equals(target)));
    }

    @Test
    void inactiveAndUnknownServicesAreRejected() {
        Service inactive = createService();
        catalogService.deactivateService(inactive.getServiceId());

        assertThrows(BusinessRuleViolationException.class, () -> availabilityService.findAvailability(
                inactive.getServiceId(), TestDates.future().toLocalDate()));
        assertThrows(ResourceNotFoundException.class, () -> availabilityService.findAvailability(
                "missing-service", TestDates.future().toLocalDate()));
    }

    @Test
    void pastDateIsRejectedAndTodayOmitsPastAndCurrentStarts() {
        Service service = createService();
        Clock todayClock = Clock.fixed(Instant.parse("2090-01-15T10:10:00Z"), ZoneOffset.UTC);
        BookingSlotPolicyService slots = new BookingSlotPolicyService(bookingRepository, bookingPolicy, todayClock);
        AvailabilityService availability = availabilityService(bookingPolicy, slots);
        LocalDate today = LocalDate.of(2090, 1, 15);

        assertThrows(BusinessRuleViolationException.class,
                () -> availability.findAvailability(service.getServiceId(), today.minusDays(1)));
        List<LocalDateTime> starts = availability.findAvailability(service.getServiceId(), today).slots().stream()
                .map(slot -> slot.startDateTime()).toList();
        assertFalse(starts.isEmpty());
        assertEquals(today.atTime(10, 30), starts.getFirst());
        assertTrue(starts.stream().allMatch(start -> start.isAfter(LocalDateTime.now(todayClock))));
    }

    private BookingManagementService bookingService(
            BookingPolicyProperties policy,
            BookingSlotPolicyService slots
    ) {
        return new BookingManagementService(
                bookingRepository, userRepository, vehicleRepository, catalogService,
                serviceOfferingService, marketplaceService,
                queueRepository, notificationRepository, notificationService, queueOrdering, coordinator,
                policy, slots, new BranchAvailabilityDecisionService(
                        bookingRepository, marketplaceService, branchSchedulingService, serviceOfferingService,
                        catalogService, policy, clock), clock);
    }

    private AvailabilityService availabilityService(
            BookingPolicyProperties policy,
            BookingSlotPolicyService slots
    ) {
        return new AvailabilityService(serviceRepository, slots, policy, coordinator);
    }
}
