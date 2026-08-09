package com.carwash.service;

import com.carwash.config.BookingPolicyProperties;
import com.carwash.domain.Service;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingSlotPolicyServiceTest extends ServiceTestSupport {

    @Test
    void candidateGenerationIsAscendingAndIncludesEveryValidStart() {
        BookingSlotPolicyService policy = policy(LocalTime.of(8, 0), LocalTime.of(10, 0), 30);
        Service service = serviceWithDuration(30);
        LocalDate date = TestDates.future().toLocalDate();

        assertEquals(List.of(
                        date.atTime(8, 0),
                        date.atTime(8, 30),
                        date.atTime(9, 0),
                        date.atTime(9, 30)),
                policy.findAvailableSlots(date, service).stream()
                        .map(BookingSlotPolicyService.AvailableSlot::startDateTime)
                        .toList());
    }

    @Test
    void serviceDurationTruncatesSlotsThatWouldFinishAfterClosing() {
        BookingSlotPolicyService policy = policy(LocalTime.of(8, 0), LocalTime.of(10, 0), 30);
        Service service = serviceWithDuration(60);
        LocalDate date = TestDates.future().toLocalDate();

        assertEquals(List.of(
                        date.atTime(8, 0),
                        date.atTime(8, 30),
                        date.atTime(9, 0)),
                policy.findAvailableSlots(date, service).stream()
                        .map(BookingSlotPolicyService.AvailableSlot::startDateTime)
                        .toList());
    }

    @Test
    void closingBoundaryAllowsAServiceEndingExactlyAtClosing() {
        BookingSlotPolicyService policy = policy(LocalTime.of(8, 0), LocalTime.of(10, 0), 30);
        Service service = serviceWithDuration(60);
        LocalDateTime valid = TestDates.future().toLocalDate().atTime(9, 0);

        policy.validateOperatingCompatibility(valid, service);
        assertThrows(BusinessRuleViolationException.class,
                () -> policy.validateOperatingCompatibility(valid.plusMinutes(30), service));
    }

    @Test
    void bookingStartMustBeStrictlyAfterApplicationClock() {
        Service service = serviceWithDuration(30);

        assertThrows(BusinessRuleViolationException.class, () -> bookingSlotPolicy.validateBookableSlot(
                null, LocalDateTime.now(clock), service, null, null));
    }

    private BookingSlotPolicyService policy(LocalTime start, LocalTime end, int intervalMinutes) {
        BookingPolicyProperties properties = new BookingPolicyProperties(
                1, Duration.ZERO, start, end, Duration.ofMinutes(intervalMinutes));
        return new BookingSlotPolicyService(bookingRepository, properties, clock);
    }

    private Service serviceWithDuration(int durationMinutes) {
        return catalogService.createService(new Service(
                ids.service(), "Duration service", "test", BigDecimal.TEN, durationMinutes));
    }
}
