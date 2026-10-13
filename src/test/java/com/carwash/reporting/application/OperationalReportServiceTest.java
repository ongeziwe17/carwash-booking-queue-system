package com.carwash.reporting.application;

import com.carwash.access.application.TenantAccessContext;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.booking.infrastructure.InMemoryBookingReportQuery;
import com.carwash.queue.application.QueueReportBucket;
import com.carwash.queue.infrastructure.InMemoryQueueReportQuery;
import com.carwash.queue.domain.QueueStatus;
import com.carwash.identity.domain.RoleName;
import com.carwash.testsupport.ServiceTestSupport;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class OperationalReportServiceTest extends ServiceTestSupport {
    private OperationalReportService service() {
        return new OperationalReportService(new InMemoryBookingReportQuery(bookingRepository),
                new InMemoryQueueReportQuery(queueRepository),marketplaceService,serviceOfferingService,
                branchSchedulingService,coordinator,clock,com.carwash.shared.application.Telemetry.NONE);
    }
    private TenantAccessContext owner(String business) {
        return new TenantAccessContext("owner",RoleName.BUSINESS_OWNER,business);
    }

    @Test void emptyDaysHaveZeroCountsAndUnavailableHistoricalCapacity() {
        String branch=ensureDefaultBranch(); LocalDate date=LocalDate.of(2089,1,15);
        var report=service().generate(owner(defaultBusinessId),date,date.plusDays(2)," "+branch+" ",null);
        assertEquals(branch,report.scopeId()); assertEquals(3,report.daily().size());
        assertEquals(0,report.summary().bookingVolume());
        assertEquals(0,report.summary().queueCompletions());
        assertEquals(0,report.summary().actualWait().sampleCount());
        assertNull(report.summary().actualWait().averageSeconds());
        assertFalse(report.summary().capacity().available());
        assertEquals("HISTORICAL_CONFIGURATION_UNAVAILABLE",report.daily().getFirst().metrics().capacity().unavailableReason());
    }

    @Test void cohortsAndLifecycleEventsUseDifferentDatesAndActualElapsedInstants() {
        String branch=ensureDefaultBranch();
        var queueEntry=createSavedQueueEntry(TestDates.futureDays(10), QueueStatus.COMPLETED);
        queueEntry.setJoinedAt(LocalDateTime.of(2089,1,15,9,0));
        queueEntry.setCalledAt(LocalDateTime.of(2089,1,15,9,5));
        queueEntry.setStartedAt(LocalDateTime.of(2089,1,15,9,20));
        queueEntry.setCompletedAt(LocalDateTime.of(2089,1,15,9,50));
        queueRepository.update(queueEntry);
        createSavedBooking(TestDates.futureDays(10).plusHours(1),BookingStatus.CANCELLED);
        LocalDate eventDay=LocalDate.of(2089,1,15), scheduled=TestDates.futureDays(10).toLocalDate();
        var events=service().generate(owner(defaultBusinessId),eventDay,eventDay,branch,null).summary();
        assertEquals(0,events.bookingVolume());assertEquals(1,events.queueJoined());
        assertEquals(1,events.serviceStarts());assertEquals(1,events.queueCompletions());
        assertEquals(1,events.actualWait().sampleCount());
        assertEquals(new BigDecimal("1200.00"),events.actualWait().averageSeconds());
        assertEquals(1200L,events.actualWait().maximumSeconds());
        assertEquals(new BigDecimal("1800.00"),events.serviceDuration().averageSeconds());
        var cohort=service().generate(owner(defaultBusinessId),scheduled,scheduled,branch,null).summary();
        assertEquals(2,cohort.bookingVolume());assertEquals(1,cohort.cancellations());
        assertEquals(new BigDecimal("0.5000"),cohort.cancellationRate());
        assertEquals(0,cohort.queueCompletions());
    }

    @Test void currentConfigurationSupportsProspectiveBookedCapacityAndCrossMidnightClipping() {
        String branch=ensureDefaultBranch();var booking=createSavedBooking(TestDates.futureDays(10),BookingStatus.CONFIRMED);
        LocalDate day=booking.getScheduledDateTime().toLocalDate();
        var result=service().generate(owner(defaultBusinessId),day,day,branch,null);
        var capacity=result.summary().capacity();
        assertTrue(capacity.available());
        assertEquals(new BigDecimal("30.00"),capacity.bookedMinutes());
        assertEquals(new BigDecimal("540.00"),capacity.availableMinutes());
        assertEquals(new BigDecimal("5.56"),capacity.utilizationPercent());
        booking.setScheduledDateTime(day.atTime(23,45)); bookingRepository.update(booking);
        var second=service().generate(owner(defaultBusinessId),day.plusDays(1),day.plusDays(1),branch,null);
        assertEquals(new BigDecimal("15.00"),second.summary().capacity().bookedMinutes());
    }

    @Test void prospectiveBookedMinutesRoundOnlyAfterPreciseMidnightClipping() {
        String branch=ensureDefaultBranch();var booking=createSavedBooking(TestDates.futureDays(10),BookingStatus.CONFIRMED);
        var day=LocalDate.of(2089,1,17);var durations=Map.of(booking.getServiceOfferingId(),30);
        var query=new InMemoryBookingReportQuery(bookingRepository);
        booking.setScheduledDateTime(day.atTime(23,59,59,700000001));bookingRepository.update(booking);
        assertEquals(new BigDecimal("0.00"),query.bookedMinutes(branch,day,durations));
        booking.setScheduledDateTime(day.atTime(23,59,59,699999999));bookingRepository.update(booking);
        assertEquals(new BigDecimal("0.01"),query.bookedMinutes(branch,day,durations));
    }

    @Test void ownerAndAdministratorScopeAreCanonicalBeforeReadingAggregates() {
        String branch=ensureDefaultBranch();LocalDate date=TestDates.futureDays(10).toLocalDate();
        createSavedBooking(TestDates.futureDays(10),BookingStatus.CONFIRMED);
        var service=service();
        assertEquals(1,service.generate(owner(defaultBusinessId),date,date,null," "+defaultBusinessId+" ").summary().bookingVolume());
        assertEquals(1,service.generate(new TenantAccessContext("admin",RoleName.PLATFORM_ADMIN,null),
                date,date," "+branch+" ",null).summary().bookingVolume());
        assertThrows(com.carwash.shared.exception.ResourceNotFoundException.class,()->service.generate(
                owner("foreign"),date,date,branch,null));
        assertThrows(com.carwash.shared.exception.ResourceNotFoundException.class,()->service.generate(
                owner(defaultBusinessId),date,date,null,"missing"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.generate(
                new TenantAccessContext("customer",RoleName.CUSTOMER,null),date,date,branch,null));
        assertThrows(com.carwash.shared.exception.BusinessRuleViolationException.class,()->service.generate(
                owner(defaultBusinessId),date,date,branch,defaultBusinessId));
        assertThrows(com.carwash.shared.exception.BusinessRuleViolationException.class,()->service.generate(
                owner(defaultBusinessId),date,date.plusDays(90),branch,null));
    }

    @Test void branchLocalEventGroupingCrossesMidnightAndDstWithoutWallClockDurationErrors() {
        String branch=ensureDefaultBranch();
        var entry=createSavedQueueEntry(TestDates.futureDays(10),QueueStatus.COMPLETED);
        entry.setJoinedAt(LocalDateTime.of(2026,3,8,6,50));
        entry.setStartedAt(LocalDateTime.of(2026,3,8,7,10));
        entry.setCompletedAt(LocalDateTime.of(2026,3,8,7,40));
        queueRepository.update(entry);
        var query=new InMemoryQueueReportQuery(queueRepository);
        var buckets=query.events(branch,LocalDate.of(2026,3,7),LocalDate.of(2026,3,9),
                ZoneId.of("America/New_York"),ZoneOffset.UTC);
        var start=buckets.stream().filter(b->b.event()==QueueReportBucket.Event.STARTED).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026,3,8),start.date());
        assertEquals(3,start.hour());
        assertEquals(1200,start.totalSeconds());
        assertEquals(1,start.sampleCount());
    }

    @Test void missingStartIsNotAnActualDurationSampleAndInvalidRuntimeGapIsExcluded() {
        String branch=ensureDefaultBranch();
        var incomplete=createSavedQueueEntry(TestDates.futureDays(10),QueueStatus.COMPLETED);
        incomplete.setJoinedAt(LocalDateTime.of(2026,11,1,1,30));
        incomplete.setStartedAt(LocalDateTime.of(2026,11,1,1,45));
        incomplete.setCompletedAt(LocalDateTime.of(2026,11,1,2,15));
        queueRepository.update(incomplete);
        var invalid=createSavedQueueEntry(TestDates.futureDays(11),QueueStatus.COMPLETED);
        invalid.setJoinedAt(LocalDateTime.of(2026,3,8,2,15));
        invalid.setStartedAt(null);
        invalid.setCompletedAt(LocalDateTime.of(2026,3,8,3,15));
        queueRepository.update(invalid);
        var query=new InMemoryQueueReportQuery(queueRepository);
        var overlap=query.events(branch,LocalDate.of(2026,11,1),LocalDate.of(2026,11,2),
                ZoneOffset.UTC,ZoneId.of("America/New_York"));
        var started=overlap.stream().filter(b->b.event()==QueueReportBucket.Event.STARTED).findFirst().orElseThrow();
        assertEquals(900,started.totalSeconds());
        assertEquals(6,started.hour()); // 01:45 EST resolves to 06:45 UTC.
        var spring=query.events(branch,LocalDate.of(2026,3,8),LocalDate.of(2026,3,9),
                ZoneId.of("America/New_York"),ZoneId.of("America/New_York"));
        assertEquals(0,spring.stream().filter(b->b.event()==QueueReportBucket.Event.JOINED)
                .mapToLong(QueueReportBucket::count).sum());
        var completed=spring.stream().filter(b->b.event()==QueueReportBucket.Event.COMPLETED).findFirst().orElseThrow();
        assertEquals(1,completed.count());
        assertEquals(0,completed.sampleCount());
        assertNull(completed.maximumSeconds());
    }

    @Test void popularityTiesUseOfferingIdAndOutOfRangePagesRetainTotals() {
        String branch=ensureDefaultBranch();var date=TestDates.futureDays(12);
        var first=createSavedBooking(date,BookingStatus.CONFIRMED);
        var second=createSavedBooking(date.plusHours(1),BookingStatus.CONFIRMED);
        var report=service().generate(owner(defaultBusinessId),date.toLocalDate(),date.toLocalDate(),branch,null);
        assertEquals(2,report.offerings().size());
        Map<String,Function<OperationalReport.Offering,?>> fields=Map.of(
                "id",OperationalReport.Offering::offeringId,
                "bookings",OperationalReport.Offering::bookingVolume,
                "serviceId",OperationalReport.Offering::serviceId);
        var firstPage=OperationalReportListPolicy.OFFERINGS.parse(Map.of("size",List.of("1"))).select(report.offerings(),fields);
        var lastPage=OperationalReportListPolicy.OFFERINGS.parse(Map.of("size",List.of("1"),"page",List.of("1")))
                .select(report.offerings(),fields);
        var empty=OperationalReportListPolicy.OFFERINGS.parse(Map.of("size",List.of("1"),"page",List.of("20")))
                .select(report.offerings(),fields);
        assertEquals(2,firstPage.totalElements());assertEquals(2,lastPage.totalElements());
        assertEquals(2,empty.totalElements());assertTrue(empty.content().isEmpty());
        assertEquals(List.of(first.getServiceOfferingId(),second.getServiceOfferingId()).stream().sorted().findFirst().orElseThrow(),
                firstPage.content().getFirst().offeringId());
    }

    @Test void businessDatesSumEachBranchLocalDayAcrossTimezones() {
        String firstBranch=ensureDefaultBranch();
        var user=registerUser();var vehicle=createVehicle(user);var serviceDefinition=createService();
        String secondBranch=ids.branch();
        marketplaceService.createBranch(com.carwash.testsupport.TestAccess.platformAdministrator(),defaultBusinessId,
                new com.carwash.marketplace.application.CreateBranchCommand(secondBranch,"New York branch",
                        "1 Main",null,"New York","NY","10001","US",BigDecimal.ZERO,BigDecimal.ZERO,
                        "America/New_York",true));
        replaceFullWeekOperatingHours(secondBranch,LocalTime.of(8,0),LocalTime.of(18,0));
        String offering=ids.offering();
        serviceOfferingService.createOffering(com.carwash.testsupport.TestAccess.platformAdministrator(),secondBranch,
                new com.carwash.catalog.application.CreateServiceOfferingCommand(offering,
                        serviceDefinition.getServiceId(),BigDecimal.TEN,30,1));
        var date=TestDates.futureDays(10);
        createSavedBooking(date,BookingStatus.CONFIRMED);
        bookingService.createBooking(com.carwash.testsupport.TestAccess.platformAdministrator(),ids.booking(),
                user.getUserId(),vehicle.getVehicleId(),secondBranch,offering,date,null);
        var report=service().generate(owner(defaultBusinessId),date.toLocalDate(),date.toLocalDate(),null,defaultBusinessId);
        assertEquals("BRANCH_LOCAL_DAYS",report.timezoneSemantics());
        assertEquals(2,report.summary().bookingVolume());
        assertEquals(2,report.branches().size());
        assertEquals(2,report.daily().getFirst().metrics().bookingVolume());
        assertEquals(java.util.Set.of("Africa/Johannesburg","America/New_York"),
                new java.util.HashSet<>(report.branches().stream().map(OperationalReport.Branch::timezone).toList()));
        assertNotEquals(firstBranch,secondBranch);
    }

    @Test void overnightWindowsAndClosuresReduceProspectiveCapacity() {
        String branch=ensureDefaultBranch();createOffering(createService());
        replaceFullWeekOperatingHours(branch,LocalTime.of(22,0),LocalTime.of(2,0));
        LocalDate day=TestDates.futureDays(10).toLocalDate();
        Instant start=day.atTime(23,0).atZone(ZoneId.of("Africa/Johannesburg")).toInstant();
        Instant end=day.plusDays(1).atTime(1,0).atZone(ZoneId.of("Africa/Johannesburg")).toInstant();
        branchSchedulingService.createTemporaryClosure(com.carwash.testsupport.TestAccess.platformAdministrator(),
                branch,new com.carwash.marketplace.application.CreateTemporaryBranchClosureCommand(ids.closure(),
                        start,end,"Closed overnight"));
        var report=service().generate(owner(defaultBusinessId),day,day.plusDays(1),branch,null);
        for (var daily:report.daily()) {
            assertTrue(daily.metrics().capacity().available());
            assertEquals(new BigDecimal("180.00"),daily.metrics().capacity().availableMinutes());
        }
    }
}
