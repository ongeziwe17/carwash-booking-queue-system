package com.carwash.reporting.application;

import com.carwash.access.application.TenantAccessContext;
import com.carwash.booking.application.*;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.catalog.application.ServiceOfferingQuery;
import com.carwash.catalog.application.ServiceOfferingSnapshot;
import com.carwash.marketplace.application.*;
import com.carwash.queue.application.*;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.application.Telemetry;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/** One authorized snapshot; business dates are sums of each branch's local day. */
public final class OperationalReportService {
    private final BookingReportQuery bookings;
    private final QueueReportQuery queue;
    private final MarketplaceQuery marketplace;
    private final ServiceOfferingQuery offerings;
    private final BranchReportingConfigurationQuery configuration;
    private final DataTransactionOperations transactions;
    private final Clock clock;
    private final Telemetry telemetry;
    public OperationalReportService(BookingReportQuery bookings, QueueReportQuery queue, MarketplaceQuery marketplace,
            ServiceOfferingQuery offerings, BranchReportingConfigurationQuery configuration,
            DataTransactionOperations transactions, Clock clock, Telemetry telemetry) {
        this.bookings=bookings; this.queue=queue; this.marketplace=marketplace; this.offerings=offerings;
        this.configuration=configuration; this.transactions=transactions; this.clock=clock; this.telemetry=telemetry;
    }

    public OperationalReport generate(TenantAccessContext access, LocalDate start, LocalDate end,
                                      String requestedBranch, String requestedBusiness) {
        Objects.requireNonNull(access);
        if (!access.isPlatformAdministrator() && access.role()!=com.carwash.identity.domain.RoleName.BUSINESS_OWNER)
            throw new AccessDeniedException("Report access is required");
        if (start==null || end==null || end.isBefore(start) || start.getYear()<1 || end.getYear()>9999
                || end.equals(LocalDate.MAX) || java.time.temporal.ChronoUnit.DAYS.between(start,end)>89)
            throw new BusinessRuleViolationException("Report requires an inclusive date range of 1-90 days");
        if ((requestedBranch==null)==(requestedBusiness==null))
            throw new BusinessRuleViolationException("Exactly one of branchId or businessId is required");
        String branchId=normalize(requestedBranch), businessId=normalize(requestedBusiness);
        return telemetry.observe(Telemetry.Operation.REPORT_READ, () -> transactions.readSnapshot(() -> {
            List<BranchSnapshot> branches;
            String scopeId,scopeType;
            if (branchId!=null) {
                BranchSnapshot branch=access.isPlatformAdministrator()
                        ? marketplace.findBranchOptional(branchId).orElseThrow(() -> missing("Branch"))
                        : marketplace.findBranchOptionalByBusiness(branchId,access.requireBusinessId())
                            .orElseThrow(() -> missing("Branch"));
                branches=List.of(branch);scopeId=branch.branchId();scopeType="BRANCH";
            } else {
                if (!access.isPlatformAdministrator() && !access.requireBusinessId().equals(businessId))
                    throw missing("Business");
                BusinessSnapshot business=marketplace.findBusinessOptional(businessId).orElseThrow(() -> missing("Business"));
                scopeId=business.businessId();scopeType="BUSINESS";
                branches=marketplace.findBranchesByBusiness(scopeId);
            }
            Map<LocalDate,Counter> days=new TreeMap<>();
            Map<String,Long> popularity=new HashMap<>();
            Map<Integer,Counter> hours=new HashMap<>();
            List<OperationalReport.Branch> branchRows=new ArrayList<>();
            Counter total=new Counter();
            for (BranchSnapshot branch:branches) {
                ZoneId zone=ZoneId.of(branch.timezone());
                Counter branchTotal=new Counter();
                Map<LocalDate,Counter> branchDays=new HashMap<>();
                for (BookingReportBucket bucket:bookings.scheduledCohorts(branch.branchId(),start,end.plusDays(1))) {
                    branchTotal.booking(bucket);
                    branchDays.computeIfAbsent(bucket.date(), ignored->new Counter()).booking(bucket);
                    if (bucket.status()!=BookingStatus.CANCELLED) popularity.merge(bucket.offeringId(),bucket.count(),Long::sum);
                    hours.computeIfAbsent(bucket.hour(),ignored->new Counter()).booking(bucket);
                }
                for (QueueReportBucket bucket:queue.events(branch.branchId(),start,end.plusDays(1),zone,clock.getZone())) {
                    branchTotal.queue(bucket);
                    branchDays.computeIfAbsent(bucket.date(), ignored->new Counter()).queue(bucket);
                    hours.computeIfAbsent(bucket.hour(),ignored->new Counter()).queue(bucket);
                }
                for (LocalDate day=start;!day.isAfter(end);day=day.plusDays(1)) {
                    Counter one=branchDays.getOrDefault(day,new Counter());
                    one.capacity=capacity(branch,day,zone);
                    branchDays.putIfAbsent(day,one);
                    days.computeIfAbsent(day,ignored->new Counter()).combine(one);
                }
                branchTotal.capacity=combineCapacity(branchDays.values());
                branchRows.add(new OperationalReport.Branch(branch.branchId(),branch.timezone(),branchTotal.metrics()));
                total.combine(branchTotal);
            }
            if (branches.isEmpty()) for (LocalDate day=start;!day.isAfter(end);day=day.plusDays(1))
                days.put(day,new Counter());
            // Include zero-booking dates even for nonempty branch scopes.
            for (LocalDate day=start;!day.isAfter(end);day=day.plusDays(1)) days.putIfAbsent(day,new Counter());
            List<OperationalReport.Offering> offeringRows=popularity.entrySet().stream().map(entry -> {
                var offering=offerings.findOfferingOptional(entry.getKey());
                return new OperationalReport.Offering(entry.getKey(),offering.map(ServiceOfferingSnapshot::serviceId)
                        .orElse(null),entry.getValue());
            }).toList();
            List<OperationalReport.PeakHour> peakRows=new ArrayList<>();
            for (int hour=0;hour<24;hour++) {
                Counter value=hours.getOrDefault(hour,new Counter());
                peakRows.add(new OperationalReport.PeakHour(hour,value.volume,value.completed));
            }
            String timezone=scopeType.equals("BRANCH") ? branches.getFirst().timezone() : "BRANCH_LOCAL_DAYS";
            return new OperationalReport(scopeType,scopeId,timezone,start,end,total.metrics(),
                    days.entrySet().stream().map(e->new OperationalReport.Daily(e.getKey(),e.getValue().metrics())).toList(),
                    branchRows,offeringRows,peakRows);
        }));
    }
    private static ResourceNotFoundException missing(String type) { return new ResourceNotFoundException(type+" not found"); }
    private static String normalize(String value) {
        if (value==null) return null;
        String trimmed=value.trim();
        if (trimmed.isBlank() || trimmed.length()>64) throw new BusinessRuleViolationException("Invalid report scope");
        return trimmed;
    }

    private OperationalMetrics.Capacity capacity(BranchSnapshot branch, LocalDate day, ZoneId zone) {
        if (!day.isAfter(clock.instant().atZone(zone).toLocalDate()) || !branch.effectiveActive())
            return unavailable("HISTORICAL_CONFIGURATION_UNAVAILABLE");
        var config=configuration.currentConfiguration(branch.branchId());
        var active=offerings.findOfferingsByBranch(branch.branchId()).stream()
                .filter(ServiceOfferingSnapshot::effectiveActive).toList();
        if (config.intervals().isEmpty() || config.scheduleUpdatedAt()==null || active.isEmpty())
            return unavailable("CAPACITY_CONFIGURATION_UNAVAILABLE");
        BigDecimal openMinutes=openMinutes(day,zone,config);
        long concurrency=active.stream().mapToLong(ServiceOfferingSnapshot::concurrentCapacity).sum();
        BigDecimal denominator=openMinutes.multiply(BigDecimal.valueOf(concurrency));
        if (denominator.signum()==0) return unavailable("ZERO_AVAILABLE_CAPACITY");
        Map<String,Integer> durations=new HashMap<>();
        active.forEach(value->durations.put(value.offeringId(),value.estimatedDurationMin()));
        BigDecimal booked=bookings.bookedMinutes(branch.branchId(),day,durations);
        return new OperationalMetrics.Capacity(true,booked,denominator,
                booked.multiply(BigDecimal.valueOf(100)).divide(denominator,2,RoundingMode.HALF_UP),null);
    }
    private static OperationalMetrics.Capacity unavailable(String reason) {
        return new OperationalMetrics.Capacity(false,null,null,null,reason);
    }
    private static BigDecimal openMinutes(LocalDate day, ZoneId zone, BranchReportingConfiguration config) {
        Instant dayStart=day.atStartOfDay(zone).toInstant(), dayEnd=day.plusDays(1).atStartOfDay(zone).toInstant();
        long seconds=0;
        for (var interval:config.intervals()) {
            for (LocalDate origin:List.of(day.minusDays(1),day)) {
                if (interval.dayOfWeek()!=origin.getDayOfWeek()) continue;
                Instant from=origin.atTime(interval.opensAt()).atZone(zone).toInstant();
                LocalDate closing=interval.closesAt().isBefore(interval.opensAt()) ? origin.plusDays(1) : origin;
                Instant to=closing.atTime(interval.closesAt()).atZone(zone).toInstant();
                from=from.isAfter(dayStart)?from:dayStart; to=to.isBefore(dayEnd)?to:dayEnd;
                if (!from.isBefore(to)) continue;
                long available=Duration.between(from,to).getSeconds();
                for (var closure:config.closures()) {
                    Instant overlapStart=closure.start().isAfter(from)?closure.start():from;
                    Instant overlapEnd=closure.end().isBefore(to)?closure.end():to;
                    if (overlapStart.isBefore(overlapEnd)) available-=Duration.between(overlapStart,overlapEnd).getSeconds();
                }
                seconds+=Math.max(0,available);
            }
        }
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(60),2,RoundingMode.HALF_UP);
    }
    private static OperationalMetrics.Capacity combineCapacity(Collection<Counter> counters) {
        if (counters.isEmpty()) return unavailable("CAPACITY_CONFIGURATION_UNAVAILABLE");
        BigDecimal booked=BigDecimal.ZERO,available=BigDecimal.ZERO;
        for (Counter counter:counters) {
            var capacity=counter.capacity;
            if (capacity==null || !capacity.available()) return unavailable("PARTIAL_CAPACITY_COVERAGE");
            booked=booked.add(capacity.bookedMinutes());available=available.add(capacity.availableMinutes());
        }
        if (available.signum()==0) return unavailable("ZERO_AVAILABLE_CAPACITY");
        return new OperationalMetrics.Capacity(true,booked,available,
                booked.multiply(BigDecimal.valueOf(100)).divide(available,2,RoundingMode.HALF_UP),null);
    }
    private static final class Counter {
        long volume, cancelled, joined, started, completed, waitSamples,waitSeconds,serviceSamples,serviceSeconds;
        Long waitMaximum,serviceMaximum;
        final EnumMap<BookingStatus,Long> statuses=new EnumMap<>(BookingStatus.class);
        OperationalMetrics.Capacity capacity;
        void booking(BookingReportBucket b) { volume+=b.count();statuses.merge(b.status(),b.count(),Long::sum);
            if (b.status()==BookingStatus.CANCELLED) cancelled+=b.count(); }
        void queue(QueueReportBucket q) {
            switch(q.event()) {
                case JOINED -> joined+=q.count();
                case STARTED -> { started+=q.count();waitSamples+=q.sampleCount();waitSeconds+=q.totalSeconds();
                    waitMaximum=max(waitMaximum,q.maximumSeconds()); }
                case COMPLETED -> { completed+=q.count();serviceSamples+=q.sampleCount();serviceSeconds+=q.totalSeconds();
                    serviceMaximum=max(serviceMaximum,q.maximumSeconds()); }
            }
        }
        void combine(Counter other) {
            volume+=other.volume;cancelled+=other.cancelled;joined+=other.joined;started+=other.started;
            completed+=other.completed;waitSamples+=other.waitSamples;waitSeconds+=other.waitSeconds;
            serviceSamples+=other.serviceSamples;serviceSeconds+=other.serviceSeconds;
            waitMaximum=max(waitMaximum,other.waitMaximum);serviceMaximum=max(serviceMaximum,other.serviceMaximum);
            other.statuses.forEach((key,count)->statuses.merge(key,count,Long::sum));
            if (capacity==null) capacity=other.capacity;
            else if (other.capacity!=null) capacity=combineCapacity(List.of(this,other));
        }
        OperationalMetrics metrics() {
            Map<String,Long> breakdown=new LinkedHashMap<>();
            for (BookingStatus status:BookingStatus.values()) breakdown.put(status.name(),statuses.getOrDefault(status,0L));
            return new OperationalMetrics(volume,breakdown,cancelled,
                    volume==0 ? null : BigDecimal.valueOf(cancelled).divide(BigDecimal.valueOf(volume),4,RoundingMode.HALF_UP),
                    joined,started,completed,samples(waitSamples,waitSeconds,waitMaximum),
                    samples(serviceSamples,serviceSeconds,serviceMaximum),capacity==null
                        ? unavailable("CAPACITY_CONFIGURATION_UNAVAILABLE"):capacity);
        }
        private static OperationalMetrics.DurationSamples samples(long count,long seconds,Long maximum) {
            return new OperationalMetrics.DurationSamples(count,count==0 ? null : BigDecimal.valueOf(seconds)
                    .divide(BigDecimal.valueOf(count),2,RoundingMode.HALF_UP),maximum);
        }
        private static Long max(Long first,Long second) {
            if (first==null) return second;
            if (second==null) return first;
            return Math.max(first,second);
        }
    }
}
