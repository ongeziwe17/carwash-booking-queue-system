package com.carwash.booking.application;

import com.carwash.booking.domain.Booking;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.catalog.application.ServiceDefinitionQuery;
import com.carwash.catalog.application.ServiceDefinitionSnapshot;
import com.carwash.catalog.application.ServiceOfferingQuery;
import com.carwash.catalog.application.ServiceOfferingSnapshot;
import com.carwash.catalog.domain.Service;
import com.carwash.identity.domain.Role;
import com.carwash.identity.domain.User;
import com.carwash.identity.domain.UserRepository;
import com.carwash.access.application.TenantAccessContext;
import com.carwash.marketplace.application.BranchSnapshot;
import com.carwash.marketplace.application.MarketplaceQuery;
import com.carwash.notification.application.BookingNotificationPublisher;
import com.carwash.notification.domain.NotificationRepository;
import com.carwash.queue.application.QueueOrderingService;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.queue.domain.QueueEntryRepository;
import com.carwash.queue.domain.QueueStatus;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.application.MutationLock;
import com.carwash.vehicle.domain.Vehicle;
import com.carwash.vehicle.domain.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import com.carwash.audit.application.*;
import com.carwash.audit.domain.AuditAction;
import com.carwash.audit.domain.AuditSource;

public class BookingManagementService implements BookingQuery {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookingManagementService.class);

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final ServiceDefinitionQuery serviceDefinitionQuery;
    private final ServiceOfferingQuery serviceOfferingQuery;
    private final MarketplaceQuery marketplaceQuery;
    private final QueueEntryRepository queueEntryRepository;
    private final NotificationRepository notificationRepository;
    private final BookingNotificationPublisher notificationPublisher;
    private final QueueOrderingService queueOrdering;
    private final DataTransactionOperations coordinator;
    private final MutationLock mutationLock;
    private final BookingPolicyProperties bookingPolicy;
    private final BookingSlotPolicyService slotPolicy;
    private final BranchAvailabilityDecisionService branchAvailability;
    private final Clock clock;
    private final AuditOperations audit;

    public BookingManagementService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            VehicleRepository vehicleRepository,
            ServiceDefinitionQuery serviceDefinitionQuery,
            ServiceOfferingQuery serviceOfferingQuery,
            MarketplaceQuery marketplaceQuery,
            QueueEntryRepository queueEntryRepository,
            NotificationRepository notificationRepository,
            BookingNotificationPublisher notificationPublisher,
            QueueOrderingService queueOrdering,
            DataTransactionOperations coordinator,
            BookingPolicyProperties bookingPolicy,
            BookingSlotPolicyService slotPolicy,
            BranchAvailabilityDecisionService branchAvailability,
            Clock clock
    ) {
        this(bookingRepository, userRepository, vehicleRepository, serviceDefinitionQuery, serviceOfferingQuery,
                marketplaceQuery, queueEntryRepository, notificationRepository, notificationPublisher,
                queueOrdering, coordinator, MutationLock.noOp(), bookingPolicy, slotPolicy,
                branchAvailability, clock, AuditOperations.noOp());
    }

    public BookingManagementService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            VehicleRepository vehicleRepository,
            ServiceDefinitionQuery serviceDefinitionQuery,
            ServiceOfferingQuery serviceOfferingQuery,
            MarketplaceQuery marketplaceQuery,
            QueueEntryRepository queueEntryRepository,
            NotificationRepository notificationRepository,
            BookingNotificationPublisher notificationPublisher,
            QueueOrderingService queueOrdering,
            DataTransactionOperations coordinator,
            MutationLock mutationLock,
            BookingPolicyProperties bookingPolicy,
            BookingSlotPolicyService slotPolicy,
            BranchAvailabilityDecisionService branchAvailability,
            Clock clock
    ) {
        this(bookingRepository, userRepository, vehicleRepository, serviceDefinitionQuery, serviceOfferingQuery,
                marketplaceQuery, queueEntryRepository, notificationRepository, notificationPublisher,
                queueOrdering, coordinator, mutationLock, bookingPolicy, slotPolicy, branchAvailability,
                clock, AuditOperations.noOp());
    }

    public BookingManagementService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            VehicleRepository vehicleRepository,
            ServiceDefinitionQuery serviceDefinitionQuery,
            ServiceOfferingQuery serviceOfferingQuery,
            MarketplaceQuery marketplaceQuery,
            QueueEntryRepository queueEntryRepository,
            NotificationRepository notificationRepository,
            BookingNotificationPublisher notificationPublisher,
            QueueOrderingService queueOrdering,
            DataTransactionOperations coordinator,
            MutationLock mutationLock,
            BookingPolicyProperties bookingPolicy,
            BookingSlotPolicyService slotPolicy,
            BranchAvailabilityDecisionService branchAvailability,
            Clock clock,
            AuditOperations audit
    ) {
        this.bookingRepository = Objects.requireNonNull(bookingRepository, "Booking repository is required");
        this.userRepository = Objects.requireNonNull(userRepository, "User repository is required");
        this.vehicleRepository = Objects.requireNonNull(vehicleRepository, "Vehicle repository is required");
        this.serviceDefinitionQuery = Objects.requireNonNull(serviceDefinitionQuery,
                "Service definition query is required");
        this.serviceOfferingQuery = Objects.requireNonNull(serviceOfferingQuery, "Service offering query is required");
        this.marketplaceQuery = Objects.requireNonNull(marketplaceQuery, "Marketplace query is required");
        this.queueEntryRepository = Objects.requireNonNull(queueEntryRepository, "Queue repository is required");
        this.notificationRepository = notificationRepository;
        this.notificationPublisher = notificationPublisher;
        this.queueOrdering = Objects.requireNonNull(queueOrdering, "Queue ordering service is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.mutationLock = Objects.requireNonNull(mutationLock, "Mutation lock is required");
        this.bookingPolicy = Objects.requireNonNull(bookingPolicy, "Booking policy is required");
        this.slotPolicy = Objects.requireNonNull(slotPolicy, "Booking slot policy is required");
        this.branchAvailability = Objects.requireNonNull(branchAvailability,
                "Branch availability decision service is required");
        this.clock = Objects.requireNonNull(clock, "Application clock is required");
        this.audit = Objects.requireNonNull(audit, "Audit operations are required");
    }

    Booking createBooking(
            String bookingId,
            String userId,
            String vehicleId,
            String branchId,
            String serviceOfferingId,
            LocalDateTime scheduledDateTime,
            String specialRequest
    ) {
        User user = new User();
        user.setUserId(userId);
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(vehicleId);
        return createBooking(new Booking(
                bookingId, user, vehicle, branchId, serviceOfferingId, null, scheduledDateTime, specialRequest));
    }

    public Booking createBooking(
            TenantAccessContext access,
            String bookingId,
            String userId,
            String vehicleId,
            String branchId,
            String serviceOfferingId,
            LocalDateTime scheduledDateTime,
            String specialRequest
    ) {
        User user = new User();
        user.setUserId(userId);
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(vehicleId);
        Booking booking = new Booking(
                bookingId, user, vehicle, branchId, serviceOfferingId, null,
                scheduledDateTime, specialRequest);
        return audit.execute(event(access, AuditAction.BOOKING_CREATED, "BOOKING", bookingId),
                () -> coordinator.write(() -> createBookingInside(access, booking)));
    }

    Booking createBooking(Booking booking) {
        return coordinator.write(() -> createBookingInside(null, booking));
    }

    private Booking createBookingInside(TenantAccessContext access, Booking booking) {
        if (booking == null) throw new BusinessRuleViolationException("Booking is required");
        if (access != null) {
            Objects.requireNonNull(access, "Tenant access context is required");
            String requestedUserId = booking.getUser() == null ? null : booking.getUser().getUserId();
            if (access.isCustomer() && !access.userId().equals(requestedUserId)) {
                throw new AccessDeniedException("Customer self-service access is required");
            }
        }
        lockNewBooking(access, booking);
        String normalizedBranchId = normalizeId(booking.getBranchId(), "Branch ID");
        BranchSnapshot branch;
        if (access != null && access.isOperational()) {
            branch = marketplaceQuery.findBranchOptionalByBusiness(normalizedBranchId, access.requireBusinessId())
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        } else {
            branch = marketplaceQuery.findBranchOptional(normalizedBranchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        }
        mutationLock.acquire(MutationLock.business(branch.businessId()));
        validateAndResolveNewBooking(booking);
        if (!bookingRepository.insert(booking)) {
            throw new BusinessRuleViolationException("Booking ID already exists");
        }
        User owner = booking.getUser();
        owner.addBooking(booking);
        if (!userRepository.update(owner)) {
            ResourceNotFoundException failure = new ResourceNotFoundException("User not found");
            coordinator.compensate(failure, () -> {
                owner.removeBooking(booking.getBookingId());
                bookingRepository.deleteForAdministrator(booking.getBookingId());
            });
            throw failure;
        }
        return booking;
    }

    public Booking findById(String bookingId) {
        return coordinator.read(() -> snapshotBooking(requireBooking(bookingId)));
    }

    public Booking findById(TenantAccessContext access, String bookingId) {
        return coordinator.read(() -> snapshotBooking(requireAccessibleBooking(access, bookingId)));
    }

    public List<Booking> findAll() {
        return findAll(null);
    }

    public List<Booking> findAll(String branchId) {
        return coordinator.read(() -> {
            List<Booking> source;
            if (branchId == null) {
                source = bookingRepository.findAll();
            } else {
                String normalizedBranchId = requireBranch(branchId).branchId();
                source = bookingRepository.findByBranchId(normalizedBranchId);
            }
            return source.stream().map(this::snapshotBooking).toList();
        });
    }

    public List<Booking> findAll(TenantAccessContext access, String branchId) {
        return findAll(access, branchId, null);
    }

    public List<Booking> findAll(
            TenantAccessContext access,
            String branchId,
            String administratorBusinessId
    ) {
        Objects.requireNonNull(access, "Tenant access context is required");
        if (access.isPlatformAdministrator()) {
            String businessId = normalizeId(administratorBusinessId, "Business ID");
            return coordinator.read(() -> {
                if (branchId == null) return bookingRepository.findByBusinessId(businessId).stream()
                        .map(this::snapshotBooking).toList();
                String normalizedBranchId = normalizeId(branchId, "Branch ID");
                marketplaceQuery.findBranchOptionalByBusiness(normalizedBranchId, businessId)
                        .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
                return bookingRepository.findByBranchIdAndBusinessId(normalizedBranchId, businessId).stream()
                        .map(this::snapshotBooking).toList();
            });
        }
        if (!access.isOperational()) throw new AccessDeniedException("Operational booking access is required");
        if (administratorBusinessId != null) {
            throw new BusinessRuleViolationException("Tenant identity is derived from authentication");
        }
        String tenantId = access.requireBusinessId();
        return coordinator.read(() -> {
            List<Booking> source;
            if (branchId == null) {
                source = bookingRepository.findByBusinessId(tenantId);
            } else {
                String normalizedBranchId = normalizeId(branchId, "Branch ID");
                marketplaceQuery.findBranchOptionalByBusiness(normalizedBranchId, tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
                source = bookingRepository.findByBranchIdAndBusinessId(normalizedBranchId, tenantId);
            }
            return source.stream().map(this::snapshotBooking).toList();
        });
    }

    @Override
    public List<BookingSnapshot> findBookingSnapshots() {
        return coordinator.read(() -> bookingRepository.findAll().stream().map(this::snapshot).toList());
    }

    @Override
    public List<BookingSnapshot> findBookingSnapshotsByBranch(String branchId) {
        return coordinator.read(() -> {
            String normalizedBranchId = requireBranch(branchId).branchId();
            return bookingRepository.findByBranchId(normalizedBranchId).stream().map(this::snapshot).toList();
        });
    }

    @Override
    public List<BookingSnapshot> findBookingSnapshotsByBusiness(String businessId) {
        return coordinator.read(() -> bookingRepository.findByBusinessId(businessId).stream()
                .map(this::snapshot).toList());
    }

    @Override
    public List<BookingSnapshot> findBookingSnapshotsByBranchAndBusiness(String branchId, String businessId) {
        return coordinator.read(() -> bookingRepository.findByBranchIdAndBusinessId(branchId, businessId).stream()
                .map(this::snapshot).toList());
    }

    @Override
    public Optional<String> findOwnerId(String bookingId) {
        return coordinator.read(() -> bookingRepository.findById(bookingId)
                .map(Booking::getUser)
                .map(User::getUserId));
    }

    @Override
    public boolean existsByUserId(String userId) {
        return coordinator.read(() -> bookingRepository.existsByUserId(userId));
    }

    @Override
    public boolean existsByVehicleId(String vehicleId) {
        return coordinator.read(() -> bookingRepository.existsByVehicleId(vehicleId));
    }

    @Override
    public boolean existsByServiceId(String serviceId) {
        return coordinator.read(() -> bookingRepository.existsByServiceId(serviceId));
    }

    Booking updateBooking(
            String bookingId,
            String vehicleId,
            String serviceOfferingId,
            String specialRequest
    ) {
        return coordinator.write(() -> updateBookingInside(
                null, bookingId, vehicleId, serviceOfferingId, specialRequest));
    }

    private Booking updateBookingInside(
            TenantAccessContext access,
            String bookingId,
            String vehicleId,
            String serviceOfferingId,
            String specialRequest
    ) {
            String normalizedBookingId = normalizeId(bookingId, "Booking ID");
            mutationLock.acquire(MutationLock.booking(normalizedBookingId));
            Booking existing = requireBookingForMutation(access, normalizedBookingId);
            mutationLock.acquire(java.util.List.of(
                    MutationLock.offering(existing.getServiceOfferingId()),
                    MutationLock.offering(serviceOfferingId),
                    MutationLock.customer(existing.getUser().getUserId()),
                    MutationLock.vehicle(vehicleId),
                    MutationLock.branch(existing.getBranchId())
            ));
            lockBookingBusiness(access, existing);
            requireModifiableBooking(existing);
            if (queueEntryRepository.existsActiveByBookingId(normalizedBookingId)) {
                throw new BusinessRuleViolationException("Booking cannot be updated while it has an active queue entry");
            }
            User owner = requireExistingOwner(existing);
            Vehicle vehicle = resolveVehicle(vehicleId);
            requireVehicleOwnedBy(vehicle, owner, "Vehicle does not belong to booking owner");
            ResolvedOffering resolved = resolveOperationalOffering(existing.getBranchId(), serviceOfferingId);
            slotPolicy.validateCustomerVehicleConflict(
                    normalizedBookingId, existing.getScheduledDateTime(), existing.getBranchId(), owner, vehicle);
            branchAvailability.requireAvailableLocal(
                    existing.getBranchId(), resolved.offering().offeringId(),
                    existing.getScheduledDateTime(), normalizedBookingId);

            Vehicle originalVehicle = existing.getVehicle();
            Service originalService = existing.getService();
            String originalOfferingId = existing.getServiceOfferingId();
            String originalSpecialRequest = existing.getSpecialRequest();
            try {
                existing.setVehicle(vehicle);
                existing.changeServiceOffering(resolved.offering().offeringId(), resolved.service());
                existing.setSpecialRequest(specialRequest);
                if (!updateBookingRecord(access, existing)) {
                    throw new ResourceNotFoundException("Booking not found");
                }
            } catch (RuntimeException exception) {
                coordinator.compensate(exception, () -> {
                    existing.setVehicle(originalVehicle);
                    existing.changeServiceOffering(originalOfferingId, originalService);
                    existing.setSpecialRequest(originalSpecialRequest);
                });
                throw exception;
            }
            return existing;
    }

    public Booking updateBooking(
            TenantAccessContext access,
            String bookingId,
            String vehicleId,
            String serviceOfferingId,
            String specialRequest
    ) {
        return audit.execute(event(access, AuditAction.BOOKING_UPDATED, "BOOKING", bookingId),
                () -> coordinator.write(() -> updateBookingInside(
                        access, bookingId, vehicleId, serviceOfferingId, specialRequest)));
    }

    Booking rescheduleBooking(String bookingId, LocalDateTime scheduledDateTime) {
        return coordinator.write(() -> rescheduleBookingInside(null, bookingId, scheduledDateTime));
    }

    private Booking rescheduleBookingInside(
            TenantAccessContext access, String bookingId, LocalDateTime scheduledDateTime) {
            String normalizedBookingId = normalizeId(bookingId, "Booking ID");
            mutationLock.acquire(MutationLock.booking(normalizedBookingId));
            Booking booking = requireBookingForMutation(access, normalizedBookingId);
            lockExistingBookingDecision(booking, false);
            lockBookingBusiness(access, booking);
            requireReschedulableBooking(booking);
            LocalDateTime now = currentBranchLocalDateTime(booking);
            requireCurrentFutureSchedule(booking, now);
            requireOpenReschedulingWindow(booking, now);
            if (queueEntryRepository.existsActiveByBookingId(normalizedBookingId)) {
                throw new BusinessRuleViolationException(
                        "Booking cannot be rescheduled while it has an active queue entry");
            }
            User owner = requireExistingOwner(booking);
            Vehicle vehicle = resolveCurrentVehicle(booking);
            requireVehicleOwnedBy(vehicle, owner, "Vehicle does not belong to booking owner");
            ResolvedOffering resolved = resolveOperationalOffering(
                    booking.getBranchId(), booking.getServiceOfferingId());
            slotPolicy.validateCustomerVehicleConflict(
                    normalizedBookingId, scheduledDateTime, booking.getBranchId(), owner, vehicle);
            branchAvailability.requireAvailableLocal(
                    booking.getBranchId(), resolved.offering().offeringId(), scheduledDateTime, normalizedBookingId);

            LocalDateTime originalScheduledDateTime = booking.getScheduledDateTime();
            try {
                booking.setScheduledDateTime(scheduledDateTime);
                if (!updateBookingRecord(access, booking)) {
                    throw new ResourceNotFoundException("Booking not found");
                }
            } catch (RuntimeException exception) {
                coordinator.compensate(exception,
                        () -> booking.setScheduledDateTime(originalScheduledDateTime));
                throw exception;
            }

            notifyCustomerBestEffort(
                    booking,
                    "BOOKING_RESCHEDULED",
                    "Your booking has been rescheduled to " + scheduledDateTime + "."
            );
            return booking;
    }

    public Booking rescheduleBooking(
            TenantAccessContext access,
            String bookingId,
            LocalDateTime scheduledDateTime
    ) {
        return audit.execute(event(access, AuditAction.BOOKING_RESCHEDULED, "BOOKING", bookingId),
                () -> coordinator.write(() -> rescheduleBookingInside(access, bookingId, scheduledDateTime)));
    }

    Booking cancelBooking(String bookingId, String customerId) {
        return coordinator.write(() -> cancelBookingInside(null, bookingId, customerId));
    }

    private Booking cancelBookingInside(
            TenantAccessContext access, String bookingId, String requestedCustomerId) {
            String normalizedBookingId = normalizeId(bookingId, "Booking ID");
            mutationLock.acquire(MutationLock.booking(normalizedBookingId));
            Booking booking = requireBookingForMutation(access, normalizedBookingId);
            lockExistingBookingDecision(booking, true);
            lockBookingBusiness(access, booking);
            String customerId = requestedCustomerId == null
                    ? requireExistingOwner(booking).getUserId()
                    : requestedCustomerId;
            validateCancellationRequest(booking, customerId);
            QueueEntry activeQueueEntry = findActiveQueueEntry(normalizedBookingId);
            if (activeQueueEntry != null && activeQueueEntry.getQueueStatus() == QueueStatus.IN_PROGRESS) {
                throw new BusinessRuleViolationException("Booking cannot be cancelled while service is in progress");
            }
            if (activeQueueEntry != null
                    && activeQueueEntry.getQueueStatus() != QueueStatus.WAITING
                    && activeQueueEntry.getQueueStatus() != QueueStatus.CALLED) {
                throw new BusinessRuleViolationException("Booking cannot be cancelled with its current queue state");
            }
            LifecycleStateSnapshot.BookingState bookingState = LifecycleStateSnapshot.booking(booking);
            List<LifecycleStateSnapshot.QueueEntryState> queueStates = activeQueueEntry == null
                    ? List.of()
                    : LifecycleStateSnapshot.queueEntries(
                            queueEntryRepository.findActiveOrderedByBranch(booking.getBranchId()));
            boolean queueRemoved = false;
            try {
                if (activeQueueEntry != null) {
                    if (!deleteQueueEntryRecord(access, activeQueueEntry.getQueueEntryId())) {
                        throw new ResourceNotFoundException("Queue entry not found");
                    }
                    queueRemoved = true;
                    if (booking.getQueueEntry() != null) {
                        booking.detachQueueEntry(booking.getQueueEntry().getQueueEntryId());
                    }
                }
                if (!booking.cancel()) {
                    throw new BusinessRuleViolationException("Invalid booking status transition");
                }
                if (!updateBookingRecord(access, booking)) {
                    throw new ResourceNotFoundException("Booking not found");
                }
                if (queueRemoved) {
                    rebalanceQueue(access, booking.getBranchId());
                }
            } catch (RuntimeException exception) {
                coordinator.compensate(exception,
                        () -> rollbackCancellation(access, exception, bookingState, queueStates));
                throw exception;
            }
            notifyCustomerBestEffort(booking, "BOOKING_CANCELLED", "Your booking has been cancelled.");
            return booking;
    }

    Booking cancelBooking(String bookingId) {
        return coordinator.write(() -> cancelBookingInside(null, bookingId, null));
    }

    public Booking cancelBooking(TenantAccessContext access, String bookingId) {
        return audit.execute(event(access, AuditAction.BOOKING_CANCELLED, "BOOKING", bookingId),
                () -> coordinator.write(() -> cancelBookingInside(access, bookingId, null)));
    }

    Booking confirmBooking(String bookingId) {
        return coordinator.write(() -> confirmBookingInside(null, bookingId));
    }

    private Booking confirmBookingInside(TenantAccessContext access, String bookingId) {
            String normalizedBookingId = normalizeId(bookingId, "Booking ID");
            mutationLock.acquire(MutationLock.booking(normalizedBookingId));
            Booking booking = requireBookingForMutation(access, normalizedBookingId);
            mutationLock.acquire(List.of(
                    MutationLock.offering(booking.getServiceOfferingId()),
                    MutationLock.branch(booking.getBranchId())));
            lockBookingBusiness(access, booking);
            resolveOperationalOffering(booking.getBranchId(), booking.getServiceOfferingId());
            if (booking.getStatus() == BookingStatus.CANCELLED) {
                throw new BusinessRuleViolationException("Cancelled booking cannot be confirmed");
            }
            if (!booking.confirm()) {
                throw new BusinessRuleViolationException("Invalid booking status transition");
            }
            if (!updateBookingRecord(access, booking)) {
                throw new ResourceNotFoundException("Booking not found");
            }
            notifyCustomer(booking, "BOOKING_CONFIRMED", "Your booking has been confirmed.");
            return booking;
    }

    public Booking confirmBooking(TenantAccessContext access, String bookingId) {
        return audit.execute(event(access, AuditAction.BOOKING_CONFIRMED, "BOOKING", bookingId),
                () -> coordinator.write(() -> {
            if (access.isCustomer()) throw new AccessDeniedException("Operational booking access is required");
            return confirmBookingInside(access, bookingId);
        }));
    }

    void deleteBooking(String bookingId) {
        coordinator.write(() -> deleteBookingInside(null, bookingId));
    }

    private void deleteBookingInside(TenantAccessContext access, String bookingId) {
            String normalizedBookingId = normalizeId(bookingId, "Booking ID");
            mutationLock.acquire(MutationLock.booking(normalizedBookingId));
            Booking booking = requireBookingForMutation(access, normalizedBookingId);
            mutationLock.acquire(java.util.List.of(
                    MutationLock.offering(booking.getServiceOfferingId()),
                    MutationLock.vehicle(booking.getVehicle().getVehicleId()),
                    MutationLock.customer(booking.getUser().getUserId()),
                    MutationLock.queueBranch(booking.getBranchId()),
                    MutationLock.branch(booking.getBranchId())
            ));
            lockBookingBusiness(access, booking);
            if (booking.getStatus() != BookingStatus.CANCELLED) {
                throw new BusinessRuleViolationException("Only cancelled bookings can be deleted");
            }
            boolean hasQueueEntry = booking.getQueueEntry() != null
                    || queueEntryRepository.existsByBookingId(normalizedBookingId);
            if (hasQueueEntry) {
                throw new BusinessRuleViolationException("Booking cannot be deleted while a queue entry references it");
            }
            User owner = requireExistingOwner(booking);
            if (notificationRepository != null) {
                notificationRepository.findByBookingId(normalizedBookingId)
                        .forEach(notification -> owner.removeNotification(notification.getNotificationId()));
                deleteBookingNotifications(access, normalizedBookingId);
            }
            owner.removeBooking(normalizedBookingId);
            if (!deleteBookingRecord(access, normalizedBookingId)) {
                throw new ResourceNotFoundException("Booking not found");
            }
            if (!userRepository.update(owner)) {
                throw new ResourceNotFoundException("User not found: " + owner.getUserId());
            }
    }

    public void deleteBooking(TenantAccessContext access, String bookingId) {
        audit.execute(event(access, AuditAction.BOOKING_DELETED, "BOOKING", bookingId),
                () -> coordinator.write(() -> deleteBookingInside(access, bookingId)));
    }

    private AuditCommand event(TenantAccessContext access, AuditAction action, String targetType, String targetId) {
        Objects.requireNonNull(access, "Tenant access context is required");
        AuditActor actor = AuditActor.user(access.userId(), access.role().name(), access.businessId());
        return AuditCommand.actionForBusiness(action, actor, access.businessId(), targetType,
                safeAuditId(targetId), AuditSource.API);
    }

    private static String safeAuditId(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() || normalized.length() > 64 ? null : normalized;
    }

    private Booking requireBooking(String bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
    }

    private Booking requireAccessibleBooking(TenantAccessContext access, String bookingId) {
        Objects.requireNonNull(access, "Tenant access context is required");
        String normalizedId = normalizeId(bookingId, "Booking ID");
        Optional<Booking> accessible = access.isPlatformAdministrator()
                ? bookingRepository.findById(normalizedId)
                : access.isOperational()
                ? bookingRepository.findByIdAndBusinessId(normalizedId, access.requireBusinessId())
                : bookingRepository.findByIdAndUserId(normalizedId, access.userId());
        return accessible.orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    private Booking requireBookingForMutation(TenantAccessContext access, String bookingId) {
        Optional<Booking> accessible = access == null || access.isPlatformAdministrator()
                ? bookingRepository.findById(bookingId)
                : access.isOperational()
                ? bookingRepository.findByIdAndBusinessId(bookingId, access.requireBusinessId())
                : bookingRepository.findByIdAndUserId(bookingId, access.userId());
        return accessible.orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    private boolean updateBookingRecord(TenantAccessContext access, Booking booking) {
        return access == null || access.isPlatformAdministrator()
                ? bookingRepository.updateForAdministrator(booking)
                : access.isOperational()
                ? bookingRepository.updateForBusiness(booking, access.requireBusinessId())
                : bookingRepository.updateForUser(booking, access.userId());
    }

    private boolean deleteBookingRecord(TenantAccessContext access, String bookingId) {
        return access == null || access.isPlatformAdministrator()
                ? bookingRepository.deleteForAdministrator(bookingId)
                : access.isOperational()
                ? bookingRepository.deleteForBusiness(bookingId, access.requireBusinessId())
                : bookingRepository.deleteForUser(bookingId, access.userId());
    }

    private boolean deleteQueueEntryRecord(TenantAccessContext access, String queueEntryId) {
        return access == null || access.isPlatformAdministrator()
                ? queueEntryRepository.deleteForAdministrator(queueEntryId)
                : access.isOperational()
                ? queueEntryRepository.deleteForBusiness(queueEntryId, access.requireBusinessId())
                : queueEntryRepository.deleteForUser(queueEntryId, access.userId());
    }

    private boolean updateQueueEntryRecord(TenantAccessContext access, QueueEntry queueEntry) {
        return access == null || access.isPlatformAdministrator()
                ? queueEntryRepository.updateForAdministrator(queueEntry)
                : access.isOperational()
                ? queueEntryRepository.updateForBusiness(queueEntry, access.requireBusinessId())
                : queueEntryRepository.updateForUser(queueEntry, access.userId());
    }

    private void deleteBookingNotifications(TenantAccessContext access, String bookingId) {
        if (access == null || access.isPlatformAdministrator()) {
            notificationRepository.deleteByBookingIdForAdministrator(bookingId);
        } else if (access.isOperational()) {
            notificationRepository.deleteByBookingIdAndBusinessId(bookingId, access.requireBusinessId());
        } else {
            notificationRepository.deleteByBookingIdAndUserId(bookingId, access.userId());
        }
    }

    private void rebalanceQueue(TenantAccessContext access, String branchId) {
        if (access != null && access.isOperational()) {
            queueOrdering.rebalanceActiveQueueForBusiness(branchId, access.requireBusinessId());
            return;
        }
        queueOrdering.rebalanceActiveQueueForAdministrator(branchId);
    }

    private void validateAndResolveNewBooking(Booking booking) {
        if (booking == null) {
            throw new BusinessRuleViolationException("Booking is required");
        }
        if (isBlank(booking.getBookingId())) {
            throw new BusinessRuleViolationException("Booking ID is required");
        }
        if (bookingRepository.existsById(booking.getBookingId().trim())) {
            throw new BusinessRuleViolationException("Booking ID already exists");
        }
        if (booking.getUser() == null || isBlank(booking.getUser().getUserId())) {
            throw new BusinessRuleViolationException("User is required");
        }
        if (booking.getVehicle() == null || isBlank(booking.getVehicle().getVehicleId())) {
            throw new BusinessRuleViolationException("Vehicle is required");
        }
        User user = resolveUser(booking.getUser().getUserId());
        Vehicle vehicle = resolveVehicle(booking.getVehicle().getVehicleId());
        requireVehicleOwnedBy(vehicle, user, "Vehicle does not belong to selected user");
        ResolvedOffering resolved = resolveOperationalOffering(
                booking.getBranchId(), booking.getServiceOfferingId());
        slotPolicy.validateCustomerVehicleConflict(
                null, booking.getScheduledDateTime(), resolved.offering().branchId(), user, vehicle);
        branchAvailability.requireAvailableLocal(
                resolved.offering().branchId(), resolved.offering().offeringId(),
                booking.getScheduledDateTime(), null);
        booking.setBookingId(booking.getBookingId().trim());
        booking.setUser(user);
        booking.setVehicle(vehicle);
        booking.assignOperationalScope(resolved.offering().branchId(), resolved.offering().offeringId());
        booking.setService(resolved.service());
        booking.setCreatedAt(LocalDateTime.now(clock));
    }

    private ResolvedOffering resolveOperationalOffering(String branchId, String offeringId) {
        BranchSnapshot branch = requireBranch(branchId);
        if (!branch.effectiveActive()) {
            throw new BusinessRuleViolationException(
                    "Inactive branch or owning business cannot accept operational bookings");
        }
        String normalizedOfferingId = normalizeId(offeringId, "Service offering ID");
        ServiceOfferingSnapshot offering = serviceOfferingQuery.findOfferingOptional(normalizedOfferingId)
                .orElseThrow(() -> new ResourceNotFoundException("Offering not found: " + normalizedOfferingId));
        if (!branch.branchId().equals(offering.branchId())) {
            throw new BusinessRuleViolationException("Service offering does not belong to the requested branch");
        }
        if (!offering.effectiveActive()) {
            throw new BusinessRuleViolationException(
                    "Inactive service offering or reusable service cannot be booked");
        }
        ServiceDefinitionSnapshot definition = serviceDefinitionQuery
                .findServiceDefinitionOptional(offering.serviceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + offering.serviceId()));
        if (!definition.active()) {
            throw new BusinessRuleViolationException("Inactive reusable service cannot be booked");
        }
        return new ResolvedOffering(offering, legacyService(definition));
    }

    private BranchSnapshot requireBranch(String branchId) {
        String normalizedBranchId = normalizeId(branchId, "Branch ID");
        return marketplaceQuery.findBranchOptional(normalizedBranchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + normalizedBranchId));
    }

    private String normalizeId(String value, String field) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new BusinessRuleViolationException(field + " is required");
        }
        if (normalized.length() > 64) {
            throw new BusinessRuleViolationException(field + " must not exceed 64 characters");
        }
        return normalized;
    }

    private Service legacyService(ServiceDefinitionSnapshot definition) {
        Service service = new Service();
        service.setServiceId(definition.serviceId());
        service.setServiceName(definition.serviceName());
        service.setDescription(definition.description());
        service.setPrice(definition.legacyDefaultPrice());
        service.setEstimatedDurationMin(definition.legacyDefaultEstimatedDurationMin());
        service.setActive(definition.active());
        service.setCreatedAt(definition.createdAt());
        return service;
    }

    private void requireModifiableBooking(Booking booking) {
        if (booking.getStatus() != BookingStatus.CREATED && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleViolationException("Booking cannot be updated in its current state");
        }
    }

    private void requireReschedulableBooking(Booking booking) {
        if (booking.getStatus() != BookingStatus.CREATED && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleViolationException("Booking cannot be rescheduled in its current state");
        }
    }

    private void requireCurrentFutureSchedule(Booking booking, LocalDateTime now) {
        if (booking.getScheduledDateTime() == null || !booking.getScheduledDateTime().isAfter(now)) {
            throw new BusinessRuleViolationException("Only future bookings can be rescheduled");
        }
    }

    private void requireOpenReschedulingWindow(Booking booking, LocalDateTime now) {
        LocalDateTime cutoff = booking.getScheduledDateTime().minus(bookingPolicy.cancellationWindow());
        if (!now.isBefore(cutoff)) {
            throw new BusinessRuleViolationException("Booking rescheduling window has closed");
        }
    }

    private User requireExistingOwner(Booking booking) {
        if (booking.getUser() == null || isBlank(booking.getUser().getUserId())) {
            throw new BusinessRuleViolationException("Booking owner is required");
        }
        return resolveUser(booking.getUser().getUserId());
    }

    private User resolveUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private Vehicle resolveVehicle(String vehicleId) {
        if (isBlank(vehicleId)) {
            throw new BusinessRuleViolationException("Vehicle ID is required");
        }
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
    }

    private Vehicle resolveCurrentVehicle(Booking booking) {
        if (booking.getVehicle() == null || isBlank(booking.getVehicle().getVehicleId())) {
            throw new BusinessRuleViolationException("Booking vehicle is required");
        }
        return resolveVehicle(booking.getVehicle().getVehicleId());
    }

    private void requireVehicleOwnedBy(Vehicle vehicle, User owner, String message) {
        if (vehicle.getUserId() == null || !vehicle.getUserId().equals(owner.getUserId())) {
            throw new BusinessRuleViolationException(message);
        }
    }

    private void validateCancellationRequest(Booking booking, String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new BusinessRuleViolationException("Customer ID is required to cancel booking");
        }
        if (booking.getUser() == null || booking.getUser().getUserId() == null
                || !booking.getUser().getUserId().equals(customerId)) {
            throw new BusinessRuleViolationException("Booking can only be cancelled by the owning customer");
        }
        if (booking.getStatus() == BookingStatus.IN_SERVICE) {
            throw new BusinessRuleViolationException("Booking cannot be cancelled while service is in progress");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BusinessRuleViolationException("Completed booking cannot be cancelled");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessRuleViolationException("Cancelled booking cannot be cancelled again");
        }
        LocalDateTime now = currentBranchLocalDateTime(booking);
        if (booking.getScheduledDateTime() == null || !now.isBefore(booking.getScheduledDateTime())) {
            throw new BusinessRuleViolationException("Only future bookings can be cancelled");
        }
        LocalDateTime cutoff = booking.getScheduledDateTime().minus(bookingPolicy.cancellationWindow());
        if (!now.isBefore(cutoff)) {
            throw new BusinessRuleViolationException("Booking cancellation window has closed");
        }
    }

    private QueueEntry findActiveQueueEntry(String bookingId) {
        return queueEntryRepository.findByBookingId(bookingId).stream()
                .filter(queueEntry -> queueEntry.getQueueStatus() != null)
                .filter(queueEntry -> queueEntry.getQueueStatus().isActive())
                .findFirst()
                .orElse(null);
    }

    private LocalDateTime currentBranchLocalDateTime(Booking booking) {
        BranchSnapshot branch = requireBranch(booking.getBranchId());
        return LocalDateTime.ofInstant(clock.instant(), ZoneId.of(branch.timezone()));
    }

    private void rollbackCancellation(
            TenantAccessContext access,
            RuntimeException failure,
            LifecycleStateSnapshot.BookingState bookingState,
            List<LifecycleStateSnapshot.QueueEntryState> queueStates
    ) {
        try {
            bookingState.restore();
            for (LifecycleStateSnapshot.QueueEntryState queueState : queueStates) {
                queueState.restore();
                QueueEntry queueEntry = queueState.queueEntry();
                if (queueEntryRepository.existsById(queueEntry.getQueueEntryId())) {
                    if (!updateQueueEntryRecord(access, queueEntry)) {
                        throw new ResourceNotFoundException("Queue entry not found");
                    }
                } else if (!queueEntryRepository.insert(queueEntry)) {
                    throw new BusinessRuleViolationException("Queue entry ID already exists");
                }
            }
            if (!updateBookingRecord(access, bookingState.booking())) {
                throw new ResourceNotFoundException("Booking not found");
            }
        } catch (RuntimeException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private void notifyCustomer(Booking booking, String type, String message) {
        if (notificationPublisher != null) {
            notificationPublisher.publishForAuthorizedBooking(booking, type, message);
        }
    }

    private void notifyCustomerBestEffort(Booking booking, String type, String message) {
        coordinator.afterCommitBestEffort(
                () -> notifyCustomer(booking, type, message),
                exception -> LOGGER.warn("Unable to create {} notification for booking {}",
                        type, booking.getBookingId(), exception));
    }

    private void lockNewBooking(TenantAccessContext access, Booking booking) {
        if (booking == null) return;
        java.util.ArrayList<String> keys = new java.util.ArrayList<>();
        if (booking.getBookingId() != null) keys.add(MutationLock.booking(booking.getBookingId().trim()));
        if (booking.getServiceOfferingId() != null) {
            keys.add(MutationLock.offering(booking.getServiceOfferingId().trim()));
        }
        if (booking.getUser() != null && booking.getUser().getUserId() != null) {
            keys.add(MutationLock.customer(booking.getUser().getUserId().trim()));
        }
        if (booking.getVehicle() != null && booking.getVehicle().getVehicleId() != null) {
            keys.add(MutationLock.vehicle(booking.getVehicle().getVehicleId().trim()));
        }
        if (booking.getBranchId() != null) {
            keys.add(MutationLock.branch(booking.getBranchId().trim()));
        }
        if (access != null && access.isOperational()) {
            keys.add(MutationLock.business(access.requireBusinessId()));
        }
        if (!keys.isEmpty()) mutationLock.acquire(keys);
    }

    private void lockExistingBookingDecision(Booking booking, boolean queueMutation) {
        java.util.ArrayList<String> keys = new java.util.ArrayList<>();
        keys.add(MutationLock.offering(booking.getServiceOfferingId()));
        if (booking.getUser() != null) keys.add(MutationLock.customer(booking.getUser().getUserId()));
        if (booking.getVehicle() != null) keys.add(MutationLock.vehicle(booking.getVehicle().getVehicleId()));
        if (queueMutation) keys.add(MutationLock.queueBranch(booking.getBranchId()));
        keys.add(MutationLock.branch(booking.getBranchId()));
        mutationLock.acquire(keys);
    }

    private void lockBookingBusiness(TenantAccessContext access, Booking booking) {
        BranchSnapshot branch = access != null && access.isOperational()
                ? marketplaceQuery.findBranchOptionalByBusiness(booking.getBranchId(), access.requireBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"))
                : marketplaceQuery.findBranchOptional(booking.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        mutationLock.acquire(MutationLock.business(branch.businessId()));
    }

    private BookingSnapshot snapshot(Booking booking) {
        return new BookingSnapshot(
                booking.getBookingId(),
                booking.getUser() == null ? null : booking.getUser().getUserId(),
                booking.getVehicle() == null ? null : booking.getVehicle().getVehicleId(),
                booking.getBranchId(),
                booking.getServiceOfferingId(),
                booking.getService() == null ? null : booking.getService().getServiceId(),
                booking.getScheduledDateTime(),
                booking.getStatus(),
                booking.getCreatedAt()
        );
    }

    private Booking snapshotBooking(Booking source) {
        Booking snapshot = new Booking();
        snapshot.setBookingId(source.getBookingId());
        snapshot.setUser(snapshotUser(source.getUser()));
        snapshot.setVehicle(snapshotVehicle(source.getVehicle()));
        snapshot.setService(snapshotService(source.getService()));
        if (source.getBranchId() != null && source.getServiceOfferingId() != null) {
            snapshot.assignOperationalScope(source.getBranchId(), source.getServiceOfferingId());
        }
        snapshot.setScheduledDateTime(source.getScheduledDateTime());
        snapshot.setStatus(source.getStatus());
        snapshot.setCreatedAt(source.getCreatedAt());
        snapshot.setSpecialRequest(source.getSpecialRequest());
        return snapshot;
    }

    private User snapshotUser(User source) {
        if (source == null) return null;
        User snapshot = new User();
        snapshot.setUserId(source.getUserId());
        snapshot.setFullName(source.getFullName());
        snapshot.setEmail(source.getEmail());
        snapshot.setPhone(source.getPhone());
        snapshot.setAccountStatus(source.getAccountStatus());
        snapshot.setCreatedAt(source.getCreatedAt());
        snapshot.setLastLoginAt(source.getLastLoginAt());
        snapshot.setRole(snapshotRole(source.getRole()));
        return snapshot;
    }

    private Role snapshotRole(Role source) {
        if (source == null) return null;
        return new Role(source.getRoleId(), source.getRoleName(), source.getDescription(), source.getPermissions());
    }

    private Vehicle snapshotVehicle(Vehicle source) {
        if (source == null) return null;
        Vehicle snapshot = new Vehicle(
                source.getVehicleId(), source.getPlateNumber(), source.getVehicleType(), source.getBrand(),
                source.getModel(), source.getColor(), source.getNotes());
        snapshot.setUserId(source.getUserId());
        return snapshot;
    }

    private Service snapshotService(Service source) {
        if (source == null) return null;
        Service snapshot = new Service();
        snapshot.setServiceId(source.getServiceId());
        snapshot.setServiceName(source.getServiceName());
        snapshot.setDescription(source.getDescription());
        snapshot.setPrice(source.getPrice());
        snapshot.setEstimatedDurationMin(source.getEstimatedDurationMin());
        snapshot.setActive(source.isActive());
        snapshot.setCreatedAt(source.getCreatedAt());
        return snapshot;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ResolvedOffering(ServiceOfferingSnapshot offering, Service service) {
    }
}
