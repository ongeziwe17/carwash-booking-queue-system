package com.carwash.marketplace.application;

import com.carwash.marketplace.domain.BranchOperatingSchedule;
import com.carwash.marketplace.domain.BranchOperatingScheduleRepository;
import com.carwash.marketplace.domain.CarWashBranch;
import com.carwash.marketplace.domain.CarWashBranchRepository;
import com.carwash.marketplace.domain.CarWashBusiness;
import com.carwash.marketplace.domain.CarWashBusinessRepository;
import com.carwash.marketplace.domain.ClosureStatus;
import com.carwash.marketplace.domain.TemporaryBranchClosure;
import com.carwash.marketplace.domain.TemporaryBranchClosureRepository;
import com.carwash.marketplace.domain.WeeklyOperatingInterval;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class BranchSchedulingService implements BranchScheduleQuery {

    private final CarWashBusinessRepository businessRepository;
    private final CarWashBranchRepository branchRepository;
    private final BranchOperatingScheduleRepository scheduleRepository;
    private final TemporaryBranchClosureRepository closureRepository;
    private final InMemoryDataCoordinator coordinator;
    private final Clock clock;

    public BranchSchedulingService(
            CarWashBusinessRepository businessRepository,
            CarWashBranchRepository branchRepository,
            BranchOperatingScheduleRepository scheduleRepository,
            TemporaryBranchClosureRepository closureRepository,
            InMemoryDataCoordinator coordinator,
            Clock clock
    ) {
        this.businessRepository = Objects.requireNonNull(businessRepository, "Business repository is required");
        this.branchRepository = Objects.requireNonNull(branchRepository, "Branch repository is required");
        this.scheduleRepository = Objects.requireNonNull(scheduleRepository, "Schedule repository is required");
        this.closureRepository = Objects.requireNonNull(closureRepository, "Closure repository is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.clock = Objects.requireNonNull(clock, "Application clock is required");
    }

    public BranchOperatingScheduleSnapshot getOperatingSchedule(String branchId) {
        return coordinator.read(() -> {
            CarWashBranch branch = requireBranch(branchId);
            return scheduleRepository.findById(branch.getBranchId())
                    .map(schedule -> BranchOperatingScheduleSnapshot.from(schedule, branch.getTimezone()))
                    .orElseGet(() -> BranchOperatingScheduleSnapshot.empty(
                            branch.getBranchId(), branch.getTimezone()));
        });
    }

    public BranchOperatingScheduleSnapshot replaceOperatingSchedule(
            String branchId,
            ReplaceOperatingScheduleCommand command
    ) {
        return coordinator.write(() -> {
            CarWashBranch branch = requireBranch(branchId);
            if (command == null || command.intervals() == null) {
                throw new BusinessRuleViolationException("Operating intervals are required");
            }
            List<WeeklyOperatingInterval> intervals = command.intervals().stream()
                    .map(this::toDomainInterval)
                    .toList();
            LocalDateTime now = LocalDateTime.now(clock);
            BranchOperatingSchedule schedule = scheduleRepository.findById(branch.getBranchId())
                    .map(existing -> existing.replace(intervals, now))
                    .orElseGet(() -> new BranchOperatingSchedule(branch.getBranchId(), intervals, now, now));

            boolean existed = scheduleRepository.existsById(branch.getBranchId());
            boolean persisted = existed ? scheduleRepository.update(schedule) : scheduleRepository.insert(schedule);
            if (!persisted) {
                throw new IllegalStateException("Operating schedule persistence state changed unexpectedly");
            }
            return BranchOperatingScheduleSnapshot.from(schedule, branch.getTimezone());
        });
    }

    public List<TemporaryBranchClosureSnapshot> listTemporaryClosures(String branchId) {
        return coordinator.read(() -> {
            CarWashBranch branch = requireBranch(branchId);
            return closureRepository.findByBranchId(branch.getBranchId()).stream()
                    .map(TemporaryBranchClosureSnapshot::from)
                    .toList();
        });
    }

    public TemporaryBranchClosureSnapshot createTemporaryClosure(
            String branchId,
            CreateTemporaryBranchClosureCommand command
    ) {
        return coordinator.write(() -> {
            CarWashBranch branch = requireBranch(branchId);
            if (command == null) {
                throw new BusinessRuleViolationException("Temporary closure request is required");
            }
            validateRequired(command.startAt(), "Closure start");
            validateRequired(command.endAt(), "Closure end");
            String closureId = normalizeRequiredId(command.closureId(), "Closure ID");
            if (closureRepository.existsById(closureId)) {
                throw new BusinessRuleViolationException("Closure ID already exists");
            }

            LocalDateTime now = LocalDateTime.now(clock);
            TemporaryBranchClosure closure = new TemporaryBranchClosure(
                    closureId,
                    branch.getBranchId(),
                    command.startAt(),
                    command.endAt(),
                    command.reason(),
                    ClosureStatus.ACTIVE,
                    now,
                    now
            );
            boolean overlaps = closureRepository.findByBranchId(branch.getBranchId()).stream()
                    .anyMatch(existing -> existing.overlaps(closure.getStartAt(), closure.getEndAt()));
            if (overlaps) {
                throw new BusinessRuleViolationException("Active temporary closures must not overlap");
            }
            if (!closureRepository.insert(closure)) {
                throw new BusinessRuleViolationException("Closure ID already exists");
            }
            return TemporaryBranchClosureSnapshot.from(closure);
        });
    }

    public TemporaryBranchClosureSnapshot cancelTemporaryClosure(String closureId) {
        return coordinator.write(() -> {
            String normalizedId = normalizeRequiredId(closureId, "Closure ID");
            TemporaryBranchClosure existing = closureRepository.findById(normalizedId)
                    .orElseThrow(() -> new ResourceNotFoundException("Closure not found: " + normalizedId));
            requireBranch(existing.getBranchId());
            if (!existing.isActive()) {
                return TemporaryBranchClosureSnapshot.from(existing);
            }
            TemporaryBranchClosure cancelled = existing.cancel(LocalDateTime.now(clock));
            if (!closureRepository.update(cancelled)) {
                throw new ResourceNotFoundException("Closure not found: " + normalizedId);
            }
            return TemporaryBranchClosureSnapshot.from(cancelled);
        });
    }

    @Override
    public BranchOpenStatusSnapshot getOpenStatus(String branchId, Instant requestedAt) {
        return coordinator.read(() -> {
            if (requestedAt == null) {
                throw new BusinessRuleViolationException("Requested instant is required");
            }
            CarWashBranch branch = requireBranch(branchId);
            CarWashBusiness business = requireBusiness(branch.getBusinessId());
            ZoneId timezone = ZoneId.of(branch.getTimezone());
            ZonedDateTime branchLocalDateTime = requestedAt.atZone(timezone);
            boolean effectiveActive = branch.isActive() && business.isActive();
            boolean withinWeeklyHours = scheduleRepository.findById(branch.getBranchId())
                    .map(schedule -> schedule.isOpenAt(branchLocalDateTime.toLocalDateTime()))
                    .orElse(false);
            TemporaryBranchClosure applicableClosure = closureRepository.findByBranchId(branch.getBranchId()).stream()
                    .filter(closure -> closure.covers(requestedAt))
                    .findFirst()
                    .orElse(null);
            boolean temporarilyClosed = applicableClosure != null;

            return new BranchOpenStatusSnapshot(
                    branch.getBranchId(),
                    requestedAt,
                    timezone.getId(),
                    branchLocalDateTime,
                    effectiveActive,
                    withinWeeklyHours,
                    temporarilyClosed,
                    effectiveActive && withinWeeklyHours && !temporarilyClosed,
                    applicableClosure == null ? null : applicableClosure.getClosureId(),
                    applicableClosure == null ? null : applicableClosure.getReason()
            );
        });
    }

    @Override
    public BranchServiceWindowSnapshot getServiceWindowStatus(
            String branchId,
            Instant startsAt,
            Instant endsAt
    ) {
        return coordinator.read(() -> {
            if (startsAt == null || endsAt == null) {
                throw new BusinessRuleViolationException("Service window start and end are required");
            }
            if (!startsAt.isBefore(endsAt)) {
                throw new BusinessRuleViolationException("Service window start must be before end");
            }

            CarWashBranch branch = requireBranch(branchId);
            CarWashBusiness business = requireBusiness(branch.getBusinessId());
            ZoneId timezone = ZoneId.of(branch.getTimezone());
            ZonedDateTime localStart = startsAt.atZone(timezone);
            ZonedDateTime localEnd = endsAt.atZone(timezone);
            boolean effectiveActive = branch.isActive() && business.isActive();
            OperatingWindowCoverage operatingWindow = scheduleRepository.findById(branch.getBranchId())
                    .map(schedule -> resolveOperatingWindow(schedule, startsAt, endsAt, timezone))
                    .orElseGet(OperatingWindowCoverage::closed);
            boolean withinWeeklyHours = operatingWindow.coversServiceWindow();
            TemporaryBranchClosure applicableClosure = closureRepository.findByBranchId(branch.getBranchId()).stream()
                    .filter(closure -> closure.overlaps(startsAt, endsAt))
                    .findFirst()
                    .orElse(null);
            boolean temporarilyClosed = applicableClosure != null;

            return new BranchServiceWindowSnapshot(
                    branch.getBranchId(),
                    startsAt,
                    endsAt,
                    timezone.getId(),
                    localStart,
                    localEnd,
                    operatingWindow.branchLocalStartsAt(),
                    effectiveActive,
                    withinWeeklyHours,
                    temporarilyClosed,
                    effectiveActive && withinWeeklyHours && !temporarilyClosed,
                    applicableClosure == null ? null : applicableClosure.getClosureId(),
                    applicableClosure == null ? null : applicableClosure.getReason()
            );
        });
    }

    private OperatingWindowCoverage resolveOperatingWindow(
            BranchOperatingSchedule schedule,
            Instant startsAt,
            Instant endsAt,
            ZoneId timezone
    ) {
        LocalDate firstLocalDate = startsAt.atZone(timezone).toLocalDate().minusDays(1);
        LocalDate lastLocalDate = endsAt.atZone(timezone).toLocalDate();
        List<InstantRange> operatingRanges = new ArrayList<>();

        for (LocalDate date = firstLocalDate; !date.isAfter(lastLocalDate); date = date.plusDays(1)) {
            DayOfWeek day = date.getDayOfWeek();
            for (WeeklyOperatingInterval interval : schedule.getIntervals()) {
                if (interval.dayOfWeek() != day) {
                    continue;
                }
                LocalDateTime localOpening = date.atTime(interval.opensAt());
                LocalDate closingDate = interval.isOvernight() ? date.plusDays(1) : date;
                LocalDateTime localClosing = closingDate.atTime(interval.closesAt());
                ZonedDateTime zonedOpening = localOpening.atZone(timezone);
                operatingRanges.add(new InstantRange(
                        zonedOpening.toInstant(),
                        localClosing.atZone(timezone).toInstant(),
                        zonedOpening
                ));
            }
        }

        operatingRanges.sort(Comparator.comparing(InstantRange::startInclusive));
        List<InstantRange> continuousRanges = new ArrayList<>();
        for (InstantRange range : operatingRanges) {
            if (continuousRanges.isEmpty()) {
                continuousRanges.add(range);
                continue;
            }
            int lastIndex = continuousRanges.size() - 1;
            InstantRange current = continuousRanges.get(lastIndex);
            if (!range.startInclusive().isAfter(current.endExclusive())) {
                continuousRanges.set(lastIndex, current.extendThrough(range));
            } else {
                continuousRanges.add(range);
            }
        }

        for (InstantRange range : continuousRanges) {
            if (!startsAt.isBefore(range.startInclusive()) && startsAt.isBefore(range.endExclusive())) {
                return new OperatingWindowCoverage(
                        !endsAt.isAfter(range.endExclusive()),
                        range.branchLocalStartsAt()
                );
            }
        }
        return OperatingWindowCoverage.closed();
    }

    private record InstantRange(
            Instant startInclusive,
            Instant endExclusive,
            ZonedDateTime branchLocalStartsAt
    ) {

        private InstantRange extendThrough(InstantRange other) {
            return other.endExclusive().isAfter(endExclusive)
                    ? new InstantRange(startInclusive, other.endExclusive(), branchLocalStartsAt)
                    : this;
        }
    }

    private record OperatingWindowCoverage(
            boolean coversServiceWindow,
            ZonedDateTime branchLocalStartsAt
    ) {

        private static OperatingWindowCoverage closed() {
            return new OperatingWindowCoverage(false, null);
        }
    }

    private WeeklyOperatingInterval toDomainInterval(WeeklyOperatingIntervalCommand command) {
        if (command == null) {
            throw new BusinessRuleViolationException("Operating intervals must not contain null values");
        }
        if (command.dayOfWeek() == null || command.opensAt() == null || command.closesAt() == null) {
            throw new BusinessRuleViolationException("Operating interval day and times are required");
        }
        return new WeeklyOperatingInterval(command.dayOfWeek(), command.opensAt(), command.closesAt());
    }

    private CarWashBranch requireBranch(String branchId) {
        String normalizedId = normalizeRequiredId(branchId, "Branch ID");
        return branchRepository.findById(normalizedId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + normalizedId));
    }

    private CarWashBusiness requireBusiness(String businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + businessId));
    }

    private String normalizeRequiredId(String value, String field) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new BusinessRuleViolationException(field + " must not be blank");
        }
        if (normalized.length() > 64) {
            throw new BusinessRuleViolationException(field + " must not exceed 64 characters");
        }
        return normalized;
    }

    private void validateRequired(Object value, String field) {
        if (value == null) {
            throw new BusinessRuleViolationException(field + " is required");
        }
    }
}
