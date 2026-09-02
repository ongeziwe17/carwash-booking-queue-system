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
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.application.MutationLock;
import com.carwash.access.application.TenantAccessContext;
import com.carwash.audit.application.*;
import com.carwash.audit.domain.AuditAction;
import com.carwash.audit.domain.AuditSource;

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
    private final DataTransactionOperations coordinator;
    private final MutationLock mutationLock;
    private final Clock clock;
    private final AuditOperations audit;

    public BranchSchedulingService(
            CarWashBusinessRepository businessRepository,
            CarWashBranchRepository branchRepository,
            BranchOperatingScheduleRepository scheduleRepository,
            TemporaryBranchClosureRepository closureRepository,
            DataTransactionOperations coordinator,
            Clock clock
    ) {
        this(businessRepository, branchRepository, scheduleRepository, closureRepository,
                coordinator, MutationLock.noOp(), clock, AuditOperations.noOp());
    }

    public BranchSchedulingService(
            CarWashBusinessRepository businessRepository,
            CarWashBranchRepository branchRepository,
            BranchOperatingScheduleRepository scheduleRepository,
            TemporaryBranchClosureRepository closureRepository,
            DataTransactionOperations coordinator,
            MutationLock mutationLock,
            Clock clock
    ) {
        this(businessRepository, branchRepository, scheduleRepository, closureRepository,
                coordinator, mutationLock, clock, AuditOperations.noOp());
    }

    public BranchSchedulingService(
            CarWashBusinessRepository businessRepository,
            CarWashBranchRepository branchRepository,
            BranchOperatingScheduleRepository scheduleRepository,
            TemporaryBranchClosureRepository closureRepository,
            DataTransactionOperations coordinator,
            MutationLock mutationLock,
            Clock clock,
            AuditOperations audit
    ) {
        this.businessRepository = Objects.requireNonNull(businessRepository, "Business repository is required");
        this.branchRepository = Objects.requireNonNull(branchRepository, "Branch repository is required");
        this.scheduleRepository = Objects.requireNonNull(scheduleRepository, "Schedule repository is required");
        this.closureRepository = Objects.requireNonNull(closureRepository, "Closure repository is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.mutationLock = Objects.requireNonNull(mutationLock, "Mutation lock is required");
        this.clock = Objects.requireNonNull(clock, "Application clock is required");
        this.audit = Objects.requireNonNull(audit, "Audit operations are required");
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

    public BranchOperatingScheduleSnapshot getOperatingSchedule(
            TenantAccessContext access,
            String branchId
    ) {
        Objects.requireNonNull(access, "Tenant access context is required");
        if (access.isPlatformAdministrator()) return getOperatingSchedule(branchId);
        String normalizedId = normalizeRequiredId(branchId, "Branch ID");
        String businessId = access.requireBusinessId();
        return coordinator.read(() -> {
            CarWashBranch branch = branchRepository.findByIdAndBusinessId(normalizedId, businessId)
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
            return scheduleRepository.findByBranchIdAndBusinessId(branch.getBranchId(), businessId)
                    .map(schedule -> BranchOperatingScheduleSnapshot.from(schedule, branch.getTimezone()))
                    .orElseGet(() -> BranchOperatingScheduleSnapshot.empty(
                            branch.getBranchId(), branch.getTimezone()));
        });
    }

    BranchOperatingScheduleSnapshot replaceOperatingSchedule(
            String branchId,
            ReplaceOperatingScheduleCommand command
    ) {
        return coordinator.write(() -> replaceOperatingScheduleInside(null, branchId, command));
    }

    public BranchOperatingScheduleSnapshot replaceOperatingSchedule(
            TenantAccessContext access,
            String branchId,
            ReplaceOperatingScheduleCommand command
    ) {
        return audit.execute(event(access, AuditAction.OPERATING_SCHEDULE_REPLACED,
                        "BRANCH_SCHEDULE", branchId),
                () -> coordinator.write(() -> replaceOperatingScheduleInside(access, branchId, command)));
    }

    public List<TemporaryBranchClosureSnapshot> listTemporaryClosures(String branchId) {
        return coordinator.read(() -> {
            CarWashBranch branch = requireBranch(branchId);
            return closureRepository.findByBranchId(branch.getBranchId()).stream()
                    .map(TemporaryBranchClosureSnapshot::from)
                    .toList();
        });
    }

    public List<TemporaryBranchClosureSnapshot> listTemporaryClosures(
            TenantAccessContext access,
            String branchId
    ) {
        if (access.isPlatformAdministrator()) return listTemporaryClosures(branchId);
        CarWashBranch branch = requireTenantBranch(branchId, access.requireBusinessId());
        return coordinator.read(() -> closureRepository
                .findByBranchIdAndBusinessId(branch.getBranchId(), access.businessId()).stream()
                .map(TemporaryBranchClosureSnapshot::from).toList());
    }

    TemporaryBranchClosureSnapshot createTemporaryClosure(
            String branchId,
            CreateTemporaryBranchClosureCommand command
    ) {
        return coordinator.write(() -> createTemporaryClosureInside(null, branchId, command));
    }

    public TemporaryBranchClosureSnapshot createTemporaryClosure(
            TenantAccessContext access,
            String branchId,
            CreateTemporaryBranchClosureCommand command
    ) {
        return audit.execute(event(access, AuditAction.TEMPORARY_CLOSURE_CREATED, "TEMPORARY_CLOSURE",
                        command == null ? null : command.closureId()),
                () -> coordinator.write(() -> createTemporaryClosureInside(access, branchId, command)));
    }

    TemporaryBranchClosureSnapshot cancelTemporaryClosure(String closureId) {
        return coordinator.write(() -> cancelTemporaryClosureInside(null, closureId));
    }

    public TemporaryBranchClosureSnapshot cancelTemporaryClosure(
            TenantAccessContext access,
            String closureId
    ) {
        return audit.execute(event(access, AuditAction.TEMPORARY_CLOSURE_CANCELLED,
                        "TEMPORARY_CLOSURE", closureId),
                () -> coordinator.write(() -> cancelTemporaryClosureInside(access, closureId)));
    }

    private AuditCommand event(TenantAccessContext access, AuditAction action, String targetType, String targetId) {
        Objects.requireNonNull(access, "Tenant access context is required");
        AuditActor actor = AuditActor.user(access.userId(), access.canonicalRoleName(), access.businessId());
        return AuditCommand.actionForBusiness(action, actor, access.businessId(), targetType,
                safeId(targetId), AuditSource.API);
    }

    private static String safeId(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() || normalized.length() > 64 ? null : normalized;
    }

    private BranchOperatingScheduleSnapshot replaceOperatingScheduleInside(
            TenantAccessContext access, String branchId, ReplaceOperatingScheduleCommand command) {
        String normalizedId = normalizeRequiredId(branchId, "Branch ID");
        mutationLock.acquire(List.of(
                MutationLock.scheduleBranch(normalizedId), MutationLock.branch(normalizedId)));
        CarWashBranch branch = requireBranchForMutation(access, normalizedId);
        mutationLock.acquire(MutationLock.business(branch.getBusinessId()));
        if (command == null || command.intervals() == null) {
            throw new BusinessRuleViolationException("Operating intervals are required");
        }
        List<WeeklyOperatingInterval> intervals = command.intervals().stream()
                .map(this::toDomainInterval).toList();
        LocalDateTime now = LocalDateTime.now(clock);
        java.util.Optional<BranchOperatingSchedule> existing = access == null || access.isPlatformAdministrator()
                ? scheduleRepository.findById(normalizedId)
                : scheduleRepository.findByBranchIdAndBusinessId(normalizedId, access.requireBusinessId());
        BranchOperatingSchedule schedule = existing
                .map(value -> value.replace(intervals, now))
                .orElseGet(() -> new BranchOperatingSchedule(normalizedId, intervals, now, now));
        boolean persisted = existing.isEmpty()
                ? scheduleRepository.insert(schedule)
                : access == null || access.isPlatformAdministrator()
                ? scheduleRepository.updateForAdministrator(schedule)
                : scheduleRepository.updateForBusiness(schedule, access.requireBusinessId());
        if (!persisted) {
            throw new ResourceNotFoundException("Operating schedule or branch not found");
        }
        return BranchOperatingScheduleSnapshot.from(schedule, branch.getTimezone());
    }

    private TemporaryBranchClosureSnapshot createTemporaryClosureInside(
            TenantAccessContext access, String branchId, CreateTemporaryBranchClosureCommand command) {
        if (command == null) throw new BusinessRuleViolationException("Temporary closure request is required");
        validateRequired(command.startAt(), "Closure start");
        validateRequired(command.endAt(), "Closure end");
        String normalizedBranchId = normalizeRequiredId(branchId, "Branch ID");
        String closureId = normalizeRequiredId(command.closureId(), "Closure ID");
        mutationLock.acquire(List.of(
                MutationLock.closure(closureId), MutationLock.scheduleBranch(normalizedBranchId),
                MutationLock.branch(normalizedBranchId)));
        CarWashBranch branch = requireBranchForMutation(access, normalizedBranchId);
        mutationLock.acquire(MutationLock.business(branch.getBusinessId()));
        if (closureRepository.existsById(closureId)) {
            throw new BusinessRuleViolationException("Closure ID already exists");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        TemporaryBranchClosure closure = new TemporaryBranchClosure(
                closureId, branch.getBranchId(), command.startAt(), command.endAt(), command.reason(),
                ClosureStatus.ACTIVE, now, now);
        List<TemporaryBranchClosure> current = access == null || access.isPlatformAdministrator()
                ? closureRepository.findByBranchId(normalizedBranchId)
                : closureRepository.findByBranchIdAndBusinessId(normalizedBranchId, access.requireBusinessId());
        if (current.stream().anyMatch(existing -> existing.overlaps(closure.getStartAt(), closure.getEndAt()))) {
            throw new BusinessRuleViolationException("Active temporary closures must not overlap");
        }
        if (!closureRepository.insert(closure)) {
            throw new BusinessRuleViolationException("Closure ID already exists");
        }
        return TemporaryBranchClosureSnapshot.from(closure);
    }

    private TemporaryBranchClosureSnapshot cancelTemporaryClosureInside(
            TenantAccessContext access, String closureId) {
        String normalizedId = normalizeRequiredId(closureId, "Closure ID");
        mutationLock.acquire(MutationLock.closure(normalizedId));
        TemporaryBranchClosure existing = requireClosureForMutation(access, normalizedId);
        mutationLock.acquire(List.of(
                MutationLock.scheduleBranch(existing.getBranchId()),
                MutationLock.branch(existing.getBranchId())));
        CarWashBranch branch = requireBranchForMutation(access, existing.getBranchId());
        mutationLock.acquire(MutationLock.business(branch.getBusinessId()));
        if (!existing.isActive()) return TemporaryBranchClosureSnapshot.from(existing);
        TemporaryBranchClosure cancelled = existing.cancel(LocalDateTime.now(clock));
        boolean updated = access == null || access.isPlatformAdministrator()
                ? closureRepository.updateForAdministrator(cancelled)
                : closureRepository.updateForBusiness(cancelled, access.requireBusinessId());
        if (!updated) throw new ResourceNotFoundException("Closure not found");
        return TemporaryBranchClosureSnapshot.from(cancelled);
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

    private CarWashBranch requireTenantBranch(String branchId, String businessId) {
        String normalizedId = normalizeRequiredId(branchId, "Branch ID");
        return coordinator.read(() -> branchRepository.findByIdAndBusinessId(normalizedId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found")));
    }

    private void requireAccessibleBranch(TenantAccessContext access, String branchId) {
        Objects.requireNonNull(access, "Tenant access context is required");
        if (!access.isPlatformAdministrator()) requireTenantBranch(branchId, access.requireBusinessId());
    }

    private CarWashBranch requireBranchForMutation(TenantAccessContext access, String branchId) {
        java.util.Optional<CarWashBranch> branch = access == null || access.isPlatformAdministrator()
                ? branchRepository.findById(branchId)
                : branchRepository.findByIdAndBusinessId(branchId, access.requireBusinessId());
        return branch.orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
    }

    private TemporaryBranchClosure requireClosureForMutation(
            TenantAccessContext access, String closureId) {
        java.util.Optional<TemporaryBranchClosure> closure = access == null || access.isPlatformAdministrator()
                ? closureRepository.findById(closureId)
                : closureRepository.findByIdAndBusinessId(closureId, access.requireBusinessId());
        return closure.orElseThrow(() -> new ResourceNotFoundException("Closure not found"));
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
