package com.carwash.service;

import com.carwash.domain.Booking;
import com.carwash.domain.Service;
import com.carwash.domain.User;
import com.carwash.domain.Vehicle;
import com.carwash.enums.BookingStatus;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BookingManagementServiceTest extends ServiceTestSupport {

    @Test
    void bookingCreationSucceeds() {
        Booking booking = newBookingWithFixture(TestDates.future());
        assertEquals(booking.getBookingId(), bookingService.createBooking(booking).getBookingId());
    }

    @Test
    void bookingCreationFailsWhenPast() {
        Booking booking = newBookingWithFixture(TestDates.past());
        assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsUnknownUser() {
        User missingUser = User.withEncodedPassword(ids.user(), "Missing", ids.emailFor(ids.user()), "123", "hash", null);
        Vehicle vehicle = new Vehicle(ids.vehicle(), ids.plate(), "Sedan", "Toyota", "Corolla", "Blue", "");
        Service service = createService();
        Booking booking = new Booking(ids.booking(), missingUser, vehicle, service, TestDates.future(), "none");
        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsUnknownVehicle() {
        User user = registerUser();
        Service service = createService();
        Vehicle missing = new Vehicle(ids.vehicle(), ids.plate(), "Sedan", "Toyota", "Corolla", "Blue", "");
        Booking booking = new Booking(ids.booking(), user, missing, service, TestDates.future(), "none");
        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsUnknownService() {
        User user = registerUser();
        Vehicle vehicle = createVehicle(user);
        Service missing = new Service(ids.service(), "Missing", "desc", BigDecimal.TEN, 30);
        Booking booking = new Booking(ids.booking(), user, vehicle, missing, TestDates.future(), "none");
        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsInactiveService() {
        User user = registerUser();
        Vehicle vehicle = createVehicle(user);
        Service service = createService();
        service.deactivate();
        Booking booking = new Booking(ids.booking(), user, vehicle, service, TestDates.future(), "none");
        assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsVehicleOwnedByDifferentUser() {
        User firstUser = registerUser();
        User secondUser = registerUser();
        Vehicle vehicle = createVehicle(secondUser);
        Service service = createService();
        Booking booking = new Booking(ids.booking(), firstUser, vehicle, service, TestDates.future(), "none");
        assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsPastScheduledDateTime() {
        Booking booking = newBookingWithFixture(TestDates.past());
        assertThrows(BusinessRuleViolationException.class, () -> bookingService.createBooking(booking));
    }

    @Test
    void createBookingRejectsFullTimeSlot() {
        LocalDateTime scheduled = TestDates.futureDays(3);
        bookingService.createBooking(newBookingWithFixture(scheduled));
        Booking overlapping = newBookingWithFixture(scheduled);
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class, () -> bookingService.createBooking(overlapping));
        assertTrue(exception.getMessage().contains("time slot"));
    }

    @Test
    void createBookingIgnoresCancelledBookingWhenCheckingSlotCapacity() {
        LocalDateTime scheduled = TestDates.futureDays(4);
        Booking cancelled = bookingService.createBooking(newBookingWithFixture(scheduled));
        bookingService.cancelBooking(cancelled.getBookingId(), cancelled.getUser().getUserId());
        Booking replacement = newBookingWithFixture(scheduled);
        assertEquals(replacement.getBookingId(), bookingService.createBooking(replacement).getBookingId());
    }

    @Test
    void createBookingRejectsSameVehicleConflictAtSameDateTime() {
        LocalDateTime scheduled = TestDates.futureDays(5);
        Booking existing = newBookingWithFixture(scheduled);
        bookingService.createBooking(existing);
        Booking conflicting = new Booking(ids.booking(), existing.getUser(), existing.getVehicle(),
                existing.getService(), scheduled, "conflict");
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class, () -> bookingService.createBooking(conflicting));
        assertTrue(exception.getMessage().contains("Customer vehicle"));
    }

    @Test
    void createBookingStillAllowsValidFutureBooking() {
        Booking booking = newBookingWithFixture(TestDates.futureDays(6));
        assertEquals(booking.getBookingId(), bookingService.createBooking(booking).getBookingId());
    }

    @Test
    void bookingConfirmationSucceeds() {
        Booking booking = createSavedBooking();
        assertEquals(BookingStatus.CONFIRMED,
                bookingService.confirmBooking(booking.getBookingId()).getStatus());
    }

    @Test
    void cancelledBookingCannotBeConfirmed() {
        Booking booking = createSavedBooking();
        bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId());
        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.confirmBooking(booking.getBookingId()));
    }

    @Test
    void customerCanCancelOwnFutureBooking() {
        Booking booking = createSavedBooking();
        Booking cancelled = bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId());
        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
        assertEquals(BookingStatus.CANCELLED, bookingService.findById(booking.getBookingId()).getStatus());
    }

    @Test
    void customerCannotCancelAnotherCustomersBooking() {
        Booking booking = createSavedBooking();
        User other = registerUser();
        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.cancelBooking(booking.getBookingId(), other.getUserId()));
        assertEquals(BookingStatus.CREATED, bookingService.findById(booking.getBookingId()).getStatus());
    }

    @Test
    void customerCannotCancelPastBooking() {
        Booking booking = createSavedBooking();
        booking.setScheduledDateTime(TestDates.past());
        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId()));
        assertEquals(BookingStatus.CREATED, bookingService.findById(booking.getBookingId()).getStatus());
    }

    @Test
    void completedBookingCannotBeCancelled() {
        Booking booking = createSavedBooking();
        assertTrue(booking.confirm());
        assertTrue(booking.startService());
        assertTrue(booking.completeService());
        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId()));
        assertEquals(BookingStatus.COMPLETED, bookingService.findById(booking.getBookingId()).getStatus());
    }

    @Test
    void alreadyCancelledBookingCannotBeCancelledAgain() {
        Booking booking = createSavedBooking();
        bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId());
        assertThrows(BusinessRuleViolationException.class,
                () -> bookingService.cancelBooking(booking.getBookingId(), booking.getUser().getUserId()));
        assertEquals(BookingStatus.CANCELLED, bookingService.findById(booking.getBookingId()).getStatus());
    }
}
