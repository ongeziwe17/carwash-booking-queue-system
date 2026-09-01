package com.carwash.workflow;

import com.carwash.booking.domain.Booking;
import com.carwash.catalog.application.CreateServiceOfferingCommand;
import com.carwash.catalog.domain.Service;
import com.carwash.identity.domain.User;
import com.carwash.marketplace.application.CreateBranchCommand;
import com.carwash.marketplace.application.UpdateBranchCommand;
import com.carwash.notification.domain.Notification;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.queue.domain.QueueStatus;
import com.carwash.reporting.api.dto.DailySummaryReportResponse;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.testsupport.ServiceTestSupport;
import com.carwash.testsupport.TestAccess;
import com.carwash.testsupport.TestDates;
import com.carwash.vehicle.domain.Vehicle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchScopedOperationsServiceTest extends ServiceTestSupport {

    @ParameterizedTest
    @EnumSource(OperationalParent.class)
    void deactivationBlocksNewAndPreStartWorkButAllowsStartedWorkToComplete(OperationalParent parent) {
        String branchId = ensureDefaultBranch();
        Service service = createService();
        String offeringId = createOffering(branchId, service, 15);

        Booking startedBooking = confirmedBooking(
                branchId, offeringId, service, TestDates.futureDays(20));
        QueueEntry started = queueService.createQueueEntry(TestAccess.platformAdministrator(),
                ids.queueEntry(), startedBooking.getBookingId(), service.getServiceId());
        queueService.callQueueEntry(TestAccess.platformAdministrator(), started.getQueueEntryId());
        queueService.startService(TestAccess.platformAdministrator(), started.getQueueEntryId());

        Booking waitingBooking = confirmedBooking(
                branchId, offeringId, service, TestDates.futureDays(21));
        QueueEntry waiting = queueService.createQueueEntry(TestAccess.platformAdministrator(),
                ids.queueEntry(), waitingBooking.getBookingId(), service.getServiceId());

        Booking calledBooking = confirmedBooking(
                branchId, offeringId, service, TestDates.futureDays(22));
        QueueEntry called = queueService.createQueueEntry(TestAccess.platformAdministrator(),
                ids.queueEntry(), calledBooking.getBookingId(), service.getServiceId());
        queueService.callQueueEntry(TestAccess.platformAdministrator(), called.getQueueEntryId());

        Booking queueCandidate = confirmedBooking(
                branchId, offeringId, service, TestDates.futureDays(23));
        User newUser = registerUser();
        Vehicle newVehicle = createVehicle(newUser);

        deactivate(parent, branchId, offeringId, service);

        assertThrows(BusinessRuleViolationException.class, () -> createAuthorizedBooking(new Booking(
                ids.booking(), newUser, newVehicle, branchId, offeringId, service,
                TestDates.futureDays(24), "inactive parent")));
        assertThrows(BusinessRuleViolationException.class, () -> queueService.createQueueEntry(
                TestAccess.platformAdministrator(),
                ids.queueEntry(), queueCandidate.getBookingId(), service.getServiceId()));
        assertThrows(BusinessRuleViolationException.class,
                () -> queueService.callQueueEntry(
                        TestAccess.platformAdministrator(), waiting.getQueueEntryId()));
        assertThrows(BusinessRuleViolationException.class,
                () -> queueService.startService(
                        TestAccess.platformAdministrator(), called.getQueueEntryId()));

        QueueEntry completed = queueService.completeQueueEntry(
                TestAccess.platformAdministrator(), started.getQueueEntryId());

        assertEquals(QueueStatus.COMPLETED, completed.getQueueStatus());
        assertEquals(com.carwash.booking.domain.BookingStatus.COMPLETED, completed.getBooking().getStatus());
        assertNotNull(completed.getCompletedAt());
        assertEquals(List.of(1, 2), queueService.findAll(branchId).stream()
                .filter(entry -> entry.getQueueStatus().isActive())
                .map(QueueEntry::getPosition)
                .toList());
        assertTrue(notificationRepository.findByBookingId(startedBooking.getBookingId()).stream()
                .anyMatch(notification -> "SERVICE_COMPLETED".equals(notification.getType())));
    }

    @Test
    void bookingScopeIsCanonicalAndOnlySameBranchOfferingCanChange() {
        String branchA = ensureDefaultBranch();
        String branchB = createBranch(defaultBusinessId, "Other Branch");
        Service exterior = createService();
        Service interior = catalogService.createService(new Service(
                ids.service(), "Interior Wash", "interior", BigDecimal.valueOf(20), 45));
        String exteriorAtA = createOffering(branchA, exterior, 20);
        String interiorAtA = createOffering(branchA, interior, 45);
        String exteriorAtB = createOffering(branchB, exterior, 30);
        User user = registerUser();
        Vehicle vehicle = createVehicle(user);

        Booking booking = createAuthorizedBooking(new Booking(
                ids.booking(), user, vehicle, branchA, exteriorAtA, interior,
                TestDates.futureDays(1), "canonical scope"));

        assertEquals(branchA, booking.getBranchId());
        assertEquals(exteriorAtA, booking.getServiceOfferingId());
        assertEquals(exterior.getServiceId(), booking.getService().getServiceId());

        Booking updated = bookingService.updateBooking(TestAccess.platformAdministrator(),
                booking.getBookingId(), vehicle.getVehicleId(), interiorAtA, "same branch");
        assertEquals(branchA, updated.getBranchId());
        assertEquals(interiorAtA, updated.getServiceOfferingId());
        assertEquals(interior.getServiceId(), updated.getService().getServiceId());

        assertThrows(BusinessRuleViolationException.class, () -> bookingService.updateBooking(
                TestAccess.platformAdministrator(),
                booking.getBookingId(), vehicle.getVehicleId(), exteriorAtB, "cross branch"));
        assertThrows(IllegalStateException.class,
                () -> booking.assignOperationalScope(branchB, exteriorAtB));

        Booking detached = bookingService.findById(booking.getBookingId());
        detached.getService().setServiceName("tampered");
        assertEquals("Interior Wash", bookingService.findById(booking.getBookingId()).getService().getServiceName());
    }

    @Test
    void parentStateIsValidatedButPrivateActiveBranchRemainsOperational() {
        String branchId = ensureDefaultBranch();
        Service service = createService();
        String offeringId = createOffering(branchId, service, 25);
        User user = registerUser();
        Vehicle vehicle = createVehicle(user);

        marketplaceService.deactivateBusiness(TestAccess.platformAdministrator(), defaultBusinessId);
        assertRejectedBooking(user, vehicle, branchId, offeringId, service);
        marketplaceService.activateBusiness(TestAccess.platformAdministrator(), defaultBusinessId);

        marketplaceService.deactivateBranch(TestAccess.platformAdministrator(), branchId);
        assertRejectedBooking(user, vehicle, branchId, offeringId, service);
        marketplaceService.activateBranch(TestAccess.platformAdministrator(), branchId);

        serviceOfferingService.deactivateOffering(TestAccess.platformAdministrator(), offeringId);
        assertRejectedBooking(user, vehicle, branchId, offeringId, service);
        serviceOfferingService.activateOffering(TestAccess.platformAdministrator(), offeringId);

        catalogService.deactivateService(service.getServiceId());
        assertRejectedBooking(user, vehicle, branchId, offeringId, service);
        catalogService.activateService(service.getServiceId());

        marketplaceService.updateBranch(TestAccess.platformAdministrator(), branchId, new UpdateBranchCommand(
                "Private Operations Branch", "1 Test Street", null, "Cape Town", "Western Cape",
                "8001", "ZA", new BigDecimal("-33.9249"), new BigDecimal("18.4241"),
                "Africa/Johannesburg", false));
        Booking privateBranchBooking = createAuthorizedBooking(new Booking(
                ids.booking(), user, vehicle, branchId, offeringId, service,
                TestDates.futureDays(2), "private is operational"));
        assertEquals(branchId, privateBranchBooking.getBranchId());

        assertThrows(ResourceNotFoundException.class, () -> createAuthorizedBooking(new Booking(
                ids.booking(), user, vehicle, "missing-branch", offeringId, service,
                TestDates.futureDays(3), "missing branch")));
        assertThrows(ResourceNotFoundException.class, () -> createAuthorizedBooking(new Booking(
                ids.booking(), user, vehicle, branchId, "missing-offering", service,
                TestDates.futureDays(3), "missing offering")));
    }

    @Test
    void queuesReportsAndNotificationsRemainIsolatedByBranch() {
        String branchA = ensureDefaultBranch();
        String branchB = createBranch(defaultBusinessId, "Second Operations Branch");
        Service service = createService();
        String offeringA = createOffering(branchA, service, 10);
        String offeringB = createOffering(branchB, service, 25);

        Booking bookingA1 = confirmedBooking(branchA, offeringA, service, TestDates.futureDays(5));
        Booking bookingA2 = confirmedBooking(branchA, offeringA, service, TestDates.futureDays(5).plusMinutes(30));
        Booking bookingB = confirmedBooking(branchB, offeringB, service, TestDates.futureDays(5));

        QueueEntry queueA1 = queueService.createQueueEntry(TestAccess.platformAdministrator(),
                ids.queueEntry(), bookingA1.getBookingId(), service.getServiceId());
        QueueEntry queueA2 = queueService.createQueueEntry(TestAccess.platformAdministrator(),
                ids.queueEntry(), bookingA2.getBookingId(), service.getServiceId());
        QueueEntry queueB = queueService.createQueueEntry(TestAccess.platformAdministrator(),
                ids.queueEntry(), bookingB.getBookingId(), service.getServiceId());

        assertEquals(List.of(1, 2), queueService.findAll(branchA).stream().map(QueueEntry::getPosition).toList());
        assertEquals(List.of(0, 10), queueService.findAll(branchA).stream()
                .map(QueueEntry::getEstimatedWaitMin).toList());
        assertEquals(1, queueService.findAll(branchB).getFirst().getPosition());
        assertEquals(0, queueService.findAll(branchB).getFirst().getEstimatedWaitMin());
        assertEquals(2, bookingService.findAll(branchA).size());
        assertEquals(1, bookingService.findAll(branchB).size());
        assertTrue(bookingService.findAll(branchA).stream().allMatch(item -> branchA.equals(item.getBranchId())));

        QueueEntry called = queueService.callNext(TestAccess.platformAdministrator(), branchB);
        assertEquals(queueB.getQueueEntryId(), called.getQueueEntryId());
        assertTrue(queueService.findAll(branchA).stream()
                .allMatch(item -> item.getQueueStatus() == QueueStatus.WAITING));
        queueService.startService(TestAccess.platformAdministrator(), queueB.getQueueEntryId());
        queueService.completeQueueEntry(TestAccess.platformAdministrator(), queueB.getQueueEntryId());
        assertEquals(List.of(queueA1.getQueueEntryId(), queueA2.getQueueEntryId()),
                queueService.findAll(branchA).stream().map(QueueEntry::getQueueEntryId).toList());
        assertEquals(List.of(1, 2), queueService.findAll(branchA).stream().map(QueueEntry::getPosition).toList());

        LocalDateTime scheduled = TestDates.futureDays(5);
        DailySummaryReportResponse branchReport = reportService.generateDailySummary(
                scheduled.toLocalDate(), branchA, null);
        DailySummaryReportResponse businessReport = reportService.generateDailySummary(
                scheduled.toLocalDate(), null, defaultBusinessId);
        assertEquals(2, branchReport.totalBookings());
        assertEquals(2, branchReport.totalQueueEntries());
        assertEquals(3, businessReport.totalBookings());
        assertEquals(3, businessReport.totalQueueEntries());
        assertEquals("Africa/Johannesburg", branchReport.timezone());

        List<Notification> branchBNotifications = notificationRepository.findByBookingId(bookingB.getBookingId());
        assertFalse(branchBNotifications.isEmpty());
        assertTrue(branchBNotifications.stream().allMatch(notification -> branchB.equals(notification.getBranchId())));
        assertTrue(branchBNotifications.stream()
                .allMatch(notification -> offeringB.equals(notification.getServiceOfferingId())));

        assertThrows(BusinessRuleViolationException.class,
                () -> reportService.generateDailySummary(scheduled.toLocalDate(), null, null));
        assertThrows(BusinessRuleViolationException.class,
                () -> reportService.generateDailySummary(scheduled.toLocalDate(), branchA, defaultBusinessId));
        assertThrows(ResourceNotFoundException.class,
                () -> bookingService.findAll("missing-branch"));
        assertThrows(ResourceNotFoundException.class,
                () -> queueService.callNext(TestAccess.platformAdministrator(), "missing-branch"));
    }

    private Booking confirmedBooking(
            String branchId,
            String offeringId,
            Service service,
            LocalDateTime scheduledDateTime
    ) {
        User user = registerUser();
        Vehicle vehicle = createVehicle(user);
        Booking booking = createAuthorizedBooking(new Booking(
                ids.booking(), user, vehicle, branchId, offeringId, service, scheduledDateTime, "branch workflow"));
        return bookingService.confirmBooking(TestAccess.platformAdministrator(), booking.getBookingId());
    }

    private void assertRejectedBooking(
            User user,
            Vehicle vehicle,
            String branchId,
            String offeringId,
            Service service
    ) {
        assertThrows(BusinessRuleViolationException.class, () -> createAuthorizedBooking(new Booking(
                ids.booking(), user, vehicle, branchId, offeringId, service,
                TestDates.futureDays(2), "inactive parent")));
    }

    private String createBranch(String businessId, String branchName) {
        String branchId = ids.branch();
        marketplaceService.createBranch(TestAccess.platformAdministrator(), businessId, new CreateBranchCommand(
                branchId, branchName, "2 Test Street", null, "Cape Town", "Western Cape", "8001", "ZA",
                new BigDecimal("-33.9250"), new BigDecimal("18.4250"), "Africa/Johannesburg", true));
        replaceFullWeekOperatingHours(branchId, java.time.LocalTime.of(8, 0), java.time.LocalTime.of(17, 0));
        return branchId;
    }

    private String createOffering(String branchId, Service service, int durationMinutes) {
        String offeringId = ids.offering();
        serviceOfferingService.createOffering(TestAccess.platformAdministrator(), branchId, new CreateServiceOfferingCommand(
                offeringId, service.getServiceId(), BigDecimal.valueOf(durationMinutes), durationMinutes, 2));
        return offeringId;
    }

    private void deactivate(
            OperationalParent parent,
            String branchId,
            String offeringId,
            Service service
    ) {
        switch (parent) {
            case BUSINESS -> marketplaceService.deactivateBusiness(TestAccess.platformAdministrator(), defaultBusinessId);
            case BRANCH -> marketplaceService.deactivateBranch(TestAccess.platformAdministrator(), branchId);
            case OFFERING -> serviceOfferingService.deactivateOffering(TestAccess.platformAdministrator(), offeringId);
            case SERVICE -> catalogService.deactivateService(service.getServiceId());
        }
    }

    private Booking createAuthorizedBooking(Booking booking) {
        return bookingService.createBooking(
                TestAccess.platformAdministrator(), booking.getBookingId(), booking.getUser().getUserId(),
                booking.getVehicle().getVehicleId(), booking.getBranchId(), booking.getServiceOfferingId(),
                booking.getScheduledDateTime(), booking.getSpecialRequest());
    }

    private enum OperationalParent {
        BUSINESS,
        BRANCH,
        OFFERING,
        SERVICE
    }
}
