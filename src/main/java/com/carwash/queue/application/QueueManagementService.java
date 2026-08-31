package com.carwash.queue.application;

import com.carwash.booking.domain.Booking;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.catalog.application.ServiceOfferingQuery;
import com.carwash.catalog.application.ServiceOfferingSnapshot;
import com.carwash.catalog.domain.Service;
import com.carwash.identity.domain.Role;
import com.carwash.identity.domain.User;
import com.carwash.access.application.TenantAccessContext;
import com.carwash.marketplace.application.BranchSnapshot;
import com.carwash.marketplace.application.MarketplaceQuery;
import com.carwash.notification.application.NotificationManagementService;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.queue.domain.QueueEntryRepository;
import com.carwash.queue.domain.QueueStatus;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.application.MutationLock;
import com.carwash.vehicle.domain.Vehicle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;

public class QueueManagementService implements QueueQuery {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueManagementService.class);

    private final QueueEntryRepository queueEntryRepository;
    private final BookingRepository bookingRepository;
    private final ServiceOfferingQuery serviceOfferingQuery;
    private final MarketplaceQuery marketplaceQuery;
    private final NotificationManagementService notificationManagementService;
    private final DataTransactionOperations coordinator;
    private final MutationLock mutationLock;
    private final QueueOrderingService queueOrdering;
    private final Clock clock;

    public QueueManagementService(
            QueueEntryRepository queueEntryRepository,
            BookingRepository bookingRepository,
            ServiceOfferingQuery serviceOfferingQuery,
            MarketplaceQuery marketplaceQuery,
            NotificationManagementService notificationManagementService,
            DataTransactionOperations coordinator,
            QueueOrderingService queueOrdering,
            Clock clock
    ) {
        this(queueEntryRepository, bookingRepository, serviceOfferingQuery, marketplaceQuery,
                notificationManagementService, coordinator, MutationLock.noOp(), queueOrdering, clock);
    }

    public QueueManagementService(
            QueueEntryRepository queueEntryRepository,
            BookingRepository bookingRepository,
            ServiceOfferingQuery serviceOfferingQuery,
            MarketplaceQuery marketplaceQuery,
            NotificationManagementService notificationManagementService,
            DataTransactionOperations coordinator,
            MutationLock mutationLock,
            QueueOrderingService queueOrdering,
            Clock clock
    ) {
        this.queueEntryRepository = Objects.requireNonNull(queueEntryRepository, "Queue repository is required");
        this.bookingRepository = Objects.requireNonNull(bookingRepository, "Booking repository is required");
        this.serviceOfferingQuery = Objects.requireNonNull(serviceOfferingQuery, "Service offering query is required");
        this.marketplaceQuery = Objects.requireNonNull(marketplaceQuery, "Marketplace query is required");
        this.notificationManagementService = notificationManagementService;
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.mutationLock = Objects.requireNonNull(mutationLock, "Mutation lock is required");
        this.queueOrdering = Objects.requireNonNull(queueOrdering, "Queue ordering service is required");
        this.clock = Objects.requireNonNull(clock, "Application clock is required");
    }

    public QueueEntry createQueueEntry(String queueEntryId, String bookingId, String serviceId) {
        Booking booking = new Booking();
        booking.setBookingId(bookingId);
        Service service = new Service();
        service.setServiceId(serviceId);
        return createQueueEntry(new QueueEntry(queueEntryId, booking, service));
    }

    public QueueEntry createQueueEntry(
            TenantAccessContext access,
            String queueEntryId,
            String bookingId,
            String serviceId
    ) {
        Objects.requireNonNull(access, "Tenant access context is required");
        if (access.isCustomer()) throw new AccessDeniedException("Operational queue access is required");
        if (access.isOperational()) {
            bookingRepository.findByIdAndBusinessId(
                            normalizeId(bookingId, "Booking ID"), access.requireBusinessId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        }
        return createQueueEntry(queueEntryId, bookingId, serviceId);
    }

    public QueueEntry createQueueEntry(QueueEntry queueEntry) {
        return coordinator.write(() -> {
            lockQueueCreation(queueEntry);
            validateAndResolveQueueEntry(queueEntry);
            Booking booking = queueEntry.getBooking();
            if (booking.getQueueEntry() != null
                    && !queueEntry.getQueueEntryId().equals(booking.getQueueEntry().getQueueEntryId())) {
                throw new BusinessRuleViolationException("Booking already has a queue entry");
            }
            String branchId = queueEntry.getBranchId();
            List<QueueEntry> activeQueue = queueEntryRepository.findActiveOrderedByBranch(branchId);
            List<LifecycleStateSnapshot.QueueEntryState> queueStates =
                    LifecycleStateSnapshot.queueEntries(activeQueue);
            LifecycleStateSnapshot.BookingState bookingState = LifecycleStateSnapshot.booking(booking);
            initializeNewQueueEntry(queueEntry, activeQueue.size() + 1);
            if (!queueEntryRepository.insert(queueEntry)) {
                throw new BusinessRuleViolationException("Queue entry ID already exists");
            }
            try {
                booking.attachQueueEntry(queueEntry);
                if (!bookingRepository.update(booking)) {
                    throw new ResourceNotFoundException("Booking not found: " + booking.getBookingId());
                }
                queueOrdering.rebalanceActiveQueue(branchId);
            } catch (RuntimeException exception) {
                coordinator.compensate(exception,
                        () -> rollbackQueueCreation(exception, queueEntry, bookingState, queueStates));
                throw exception;
            }
            return snapshotQueueEntry(requireQueueEntry(queueEntry.getQueueEntryId()));
        });
    }

    public QueueEntry findById(String queueEntryId) {
        return coordinator.read(() -> snapshotQueueEntry(requireQueueEntry(queueEntryId)));
    }

    public QueueEntry findById(TenantAccessContext access, String queueEntryId) {
        return coordinator.read(() -> snapshotQueueEntry(requireAccessibleQueueEntry(access, queueEntryId)));
    }

    public List<QueueEntry> findAll() {
        return findAll(null);
    }

    public List<QueueEntry> findAll(String branchId) {
        return coordinator.read(() -> {
            List<QueueEntry> source;
            if (branchId == null) {
                source = queueEntryRepository.findAllOrdered();
            } else {
                String normalizedBranchId = requireBranch(branchId).branchId();
                source = queueEntryRepository.findByBranchId(normalizedBranchId);
            }
            return snapshotQueueEntries(source);
        });
    }

    public List<QueueEntry> findAll(TenantAccessContext access, String branchId) {
        return findAll(access, branchId, null);
    }

    public List<QueueEntry> findAll(
            TenantAccessContext access,
            String branchId,
            String administratorBusinessId
    ) {
        Objects.requireNonNull(access, "Tenant access context is required");
        if (access.isPlatformAdministrator()) {
            String businessId = normalizeId(administratorBusinessId, "Business ID");
            return coordinator.read(() -> {
                if (branchId == null) return snapshotQueueEntries(
                        queueEntryRepository.findByBusinessId(businessId));
                String normalizedBranchId = normalizeId(branchId, "Branch ID");
                marketplaceQuery.findBranchOptionalByBusiness(normalizedBranchId, businessId)
                        .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
                return snapshotQueueEntries(queueEntryRepository
                        .findByBranchIdAndBusinessId(normalizedBranchId, businessId));
            });
        }
        if (!access.isOperational()) throw new AccessDeniedException("Operational queue access is required");
        if (administratorBusinessId != null) {
            throw new BusinessRuleViolationException("Tenant identity is derived from authentication");
        }
        String tenantId = access.requireBusinessId();
        return coordinator.read(() -> {
            List<QueueEntry> source;
            if (branchId == null) {
                source = queueEntryRepository.findByBusinessId(tenantId);
            } else {
                String normalizedBranchId = normalizeId(branchId, "Branch ID");
                marketplaceQuery.findBranchOptionalByBusiness(normalizedBranchId, tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
                source = queueEntryRepository.findByBranchIdAndBusinessId(normalizedBranchId, tenantId);
            }
            return snapshotQueueEntries(source);
        });
    }

    @Override
    public List<QueueEntrySnapshot> findQueueEntrySnapshots() {
        return coordinator.read(() -> queueEntrySnapshots(queueEntryRepository.findAllOrdered()));
    }

    @Override
    public List<QueueEntrySnapshot> findQueueEntrySnapshotsByBranch(String branchId) {
        return coordinator.read(() -> {
            String normalizedBranchId = requireBranch(branchId).branchId();
            return queueEntrySnapshots(queueEntryRepository.findByBranchId(normalizedBranchId));
        });
    }

    @Override
    public List<QueueEntrySnapshot> findQueueEntrySnapshotsByBusiness(String businessId) {
        return coordinator.read(() -> queueEntrySnapshots(queueEntryRepository.findByBusinessId(businessId)));
    }

    @Override
    public List<QueueEntrySnapshot> findQueueEntrySnapshotsByBranchAndBusiness(
            String branchId,
            String businessId
    ) {
        return coordinator.read(() -> queueEntrySnapshots(
                queueEntryRepository.findByBranchIdAndBusinessId(branchId, businessId)));
    }

    @Override
    public int estimateWaitMinutesForNewWork(String branchId) {
        return coordinator.read(() -> {
            String normalizedBranchId = requireBranch(branchId).branchId();
            int waitMinutes = 0;
            for (QueueEntry entry : queueEntryRepository.findActiveOrderedByBranch(normalizedBranchId)) {
                ServiceOfferingSnapshot offering = requireOffering(entry.getServiceOfferingId());
                waitMinutes = Math.addExact(waitMinutes, offering.estimatedDurationMin());
            }
            return waitMinutes;
        });
    }

    @Override
    public Optional<String> findOwnerId(String queueEntryId) {
        return coordinator.read(() -> queueEntryRepository.findById(queueEntryId)
                .map(QueueEntry::getBooking)
                .map(Booking::getBookingId)
                .flatMap(bookingRepository::findById)
                .map(Booking::getUser)
                .map(User::getUserId));
    }

    @Override
    public boolean existsByServiceId(String serviceId) {
        return coordinator.read(() -> queueEntryRepository.existsByServiceId(serviceId));
    }

    public List<QueueEntry> findByServiceId(String serviceId) {
        return coordinator.read(() -> snapshotQueueEntries(queueEntryRepository.findByServiceId(serviceId)));
    }

    public QueueEntry updatePosition(String queueEntryId, int position) {
        return coordinator.write(() -> {
            if (position <= 0) {
                throw new BusinessRuleViolationException("Queue position must be positive");
            }
            QueueEntry initial = requireQueueEntry(queueEntryId);
            mutationLock.acquire(MutationLock.queueBranch(initial.getBranchId()));
            QueueEntry queueEntry = requireQueueEntry(queueEntryId);
            if (queueEntry.getQueueStatus() != QueueStatus.WAITING) {
                throw new BusinessRuleViolationException("Only waiting queue entries can be repositioned");
            }
            List<QueueEntry> activeQueue = new ArrayList<>(
                    queueEntryRepository.findActiveOrderedByBranch(queueEntry.getBranchId()));
            if (position > activeQueue.size()) {
                throw new BusinessRuleViolationException("Queue position exceeds active queue size");
            }
            activeQueue.removeIf(entry -> queueEntryId.equals(entry.getQueueEntryId()));
            activeQueue.add(position - 1, queueEntry);
            queueOrdering.rebalanceActiveQueue(activeQueue);
            return snapshotQueueEntry(queueEntry);
        });
    }

    public QueueEntry updatePosition(TenantAccessContext access, String queueEntryId, int position) {
        requireOperationalQueueEntry(access, queueEntryId);
        return updatePosition(queueEntryId, position);
    }

    public QueueEntry callNext(String branchId) {
        return coordinator.write(() -> {
            String normalizedBranchId = requireBranch(branchId).branchId();
            mutationLock.acquire(MutationLock.queueBranch(normalizedBranchId));
            return callWaitingEntry(queueEntryRepository.findNextWaitingByBranch(normalizedBranchId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No waiting queue entry available for branch: " + normalizedBranchId)));
        });
    }

    public QueueEntry callNext(TenantAccessContext access, String branchId) {
        Objects.requireNonNull(access, "Tenant access context is required");
        if (access.isCustomer()) throw new AccessDeniedException("Operational queue access is required");
        if (access.isPlatformAdministrator()) return callNext(branchId);
        return coordinator.write(() -> {
            String normalizedBranchId = normalizeId(branchId, "Branch ID");
            String tenantId = access.requireBusinessId();
            marketplaceQuery.findBranchOptionalByBusiness(normalizedBranchId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
            mutationLock.acquire(MutationLock.queueBranch(normalizedBranchId));
            return callWaitingEntry(queueEntryRepository
                    .findNextWaitingByBranchIdAndBusinessId(normalizedBranchId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("No waiting queue entry found")));
        });
    }

    public QueueEntry callQueueEntry(String queueEntryId) {
        return coordinator.write(() -> {
            QueueEntry initial = requireQueueEntry(queueEntryId);
            mutationLock.acquire(MutationLock.queueBranch(initial.getBranchId()));
            return callWaitingEntry(requireQueueEntry(queueEntryId));
        });
    }

    public QueueEntry callQueueEntry(TenantAccessContext access, String queueEntryId) {
        requireOperationalQueueEntry(access, queueEntryId);
        return callQueueEntry(queueEntryId);
    }

    public QueueEntry startService(String queueEntryId) {
        return coordinator.write(() -> {
            QueueEntry initial = requireQueueEntry(queueEntryId);
            lockQueueLifecycle(initial);
            QueueEntry queueEntry = requireQueueEntry(queueEntryId);
            if (queueEntry.getQueueStatus() != QueueStatus.CALLED) {
                throw new BusinessRuleViolationException("Queue entry cannot start service in current state");
            }
            Booking booking = requireCanonicalBooking(queueEntry);
            CanonicalQueueScope scope = validateCanonicalScopeIntegrity(queueEntry, booking);
            validateOperationalEligibility(scope);
            if (booking.getStatus() != BookingStatus.CONFIRMED) {
                throw new BusinessRuleViolationException("Booking must be confirmed before service can start");
            }
            LifecycleStateSnapshot.QueueEntryState queueState = LifecycleStateSnapshot.queueEntry(queueEntry);
            LifecycleStateSnapshot.BookingState bookingState = LifecycleStateSnapshot.booking(booking);
            try {
                queueEntry.setBooking(booking);
                if (!queueEntry.startService(LocalDateTime.now(clock))) {
                    throw new BusinessRuleViolationException("Queue entry cannot start service in current state");
                }
                if (!booking.startService()) {
                    throw new BusinessRuleViolationException("Booking must be confirmed before service can start");
                }
                updateQueueEntry(queueEntry);
                updateBooking(booking);
            } catch (RuntimeException exception) {
                coordinator.compensate(exception,
                        () -> rollbackLifecycle(exception, bookingState, List.of(queueState)));
                throw exception;
            }
            notifyCustomerBestEffort(queueEntry, "SERVICE_STARTED", "Your service has started.");
            return snapshotQueueEntry(queueEntry);
        });
    }

    public QueueEntry startService(TenantAccessContext access, String queueEntryId) {
        requireOperationalQueueEntry(access, queueEntryId);
        return startService(queueEntryId);
    }

    public QueueEntry completeQueueEntry(String queueEntryId) {
        return coordinator.write(() -> {
            QueueEntry initial = requireQueueEntry(queueEntryId);
            lockQueueLifecycle(initial);
            QueueEntry queueEntry = requireQueueEntry(queueEntryId);
            if (queueEntry.getStartedAt() == null) {
                throw new BusinessRuleViolationException("Queue entry cannot be completed before it has started");
            }
            if (queueEntry.getQueueStatus() != QueueStatus.IN_PROGRESS) {
                throw new BusinessRuleViolationException("Queue entry cannot be completed in current state");
            }
            Booking booking = requireCanonicalBooking(queueEntry);
            validateCanonicalScopeIntegrity(queueEntry, booking);
            if (booking.getStatus() != BookingStatus.IN_SERVICE) {
                throw new BusinessRuleViolationException("Booking must be in service before queue completion");
            }
            List<LifecycleStateSnapshot.QueueEntryState> queueStates = LifecycleStateSnapshot.queueEntries(
                    queueEntryRepository.findActiveOrderedByBranch(queueEntry.getBranchId()));
            LifecycleStateSnapshot.BookingState bookingState = LifecycleStateSnapshot.booking(booking);
            try {
                queueEntry.setBooking(booking);
                if (!queueEntry.complete(LocalDateTime.now(clock))) {
                    throw new BusinessRuleViolationException("Queue entry cannot be completed in current state");
                }
                if (!booking.completeService()) {
                    throw new BusinessRuleViolationException("Booking must be in service before queue completion");
                }
                updateQueueEntry(queueEntry);
                updateBooking(booking);
                queueOrdering.rebalanceActiveQueue(queueEntry.getBranchId());
            } catch (RuntimeException exception) {
                coordinator.compensate(exception,
                        () -> rollbackLifecycle(exception, bookingState, queueStates));
                throw exception;
            }
            notifyCustomerBestEffort(queueEntry, "SERVICE_COMPLETED", "Your service has been completed.");
            return snapshotQueueEntry(queueEntry);
        });
    }

    public QueueEntry completeQueueEntry(TenantAccessContext access, String queueEntryId) {
        requireOperationalQueueEntry(access, queueEntryId);
        return completeQueueEntry(queueEntryId);
    }

    public void deleteQueueEntry(String queueEntryId) {
        coordinator.write(() -> {
            QueueEntry initial = requireQueueEntry(queueEntryId);
            lockQueueLifecycle(initial);
            QueueEntry queueEntry = requireQueueEntry(queueEntryId);
            if (queueEntry.getQueueStatus() != QueueStatus.WAITING) {
                throw new BusinessRuleViolationException("Only waiting queue entries can be deleted");
            }
            Booking booking = requireCanonicalBooking(queueEntry);
            List<LifecycleStateSnapshot.QueueEntryState> queueStates = LifecycleStateSnapshot.queueEntries(
                    queueEntryRepository.findActiveOrderedByBranch(queueEntry.getBranchId()));
            LifecycleStateSnapshot.BookingState bookingState = booking == null
                    ? null
                    : LifecycleStateSnapshot.booking(booking);
            try {
                if (!queueEntryRepository.deleteById(queueEntryId)) {
                    throw new ResourceNotFoundException("Queue entry not found: " + queueEntryId);
                }
                if (booking != null) {
                    booking.detachQueueEntry(queueEntryId);
                    updateBooking(booking);
                }
                queueOrdering.rebalanceActiveQueue(queueEntry.getBranchId());
            } catch (RuntimeException exception) {
                coordinator.compensate(exception,
                        () -> rollbackQueueDeletion(exception, bookingState, queueStates));
                throw exception;
            }
        });
    }

    public void deleteQueueEntry(TenantAccessContext access, String queueEntryId) {
        requireOperationalQueueEntry(access, queueEntryId);
        deleteQueueEntry(queueEntryId);
    }

    private void rollbackQueueCreation(
            RuntimeException failure,
            QueueEntry insertedEntry,
            LifecycleStateSnapshot.BookingState bookingState,
            List<LifecycleStateSnapshot.QueueEntryState> queueStates
    ) {
        try {
            queueEntryRepository.deleteById(insertedEntry.getQueueEntryId());
            restoreQueueAndBookingStates(bookingState, queueStates);
        } catch (RuntimeException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private void rollbackQueueDeletion(
            RuntimeException failure,
            LifecycleStateSnapshot.BookingState bookingState,
            List<LifecycleStateSnapshot.QueueEntryState> queueStates
    ) {
        try {
            for (LifecycleStateSnapshot.QueueEntryState queueState : queueStates) {
                queueState.restore();
                QueueEntry entry = queueState.queueEntry();
                if (queueEntryRepository.existsById(entry.getQueueEntryId())) {
                    updateQueueEntry(entry);
                } else if (!queueEntryRepository.insert(entry)) {
                    throw new BusinessRuleViolationException("Queue entry ID already exists");
                }
            }
            if (bookingState != null) {
                bookingState.restore();
                updateBooking(bookingState.booking());
            }
        } catch (RuntimeException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private void restoreQueueAndBookingStates(
            LifecycleStateSnapshot.BookingState bookingState,
            List<LifecycleStateSnapshot.QueueEntryState> queueStates
    ) {
        for (LifecycleStateSnapshot.QueueEntryState queueState : queueStates) {
            queueState.restore();
            updateQueueEntry(queueState.queueEntry());
        }
        bookingState.restore();
        updateBooking(bookingState.booking());
    }

    private QueueEntry requireQueueEntry(String queueEntryId) {
        return queueEntryRepository.findById(queueEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue entry not found: " + queueEntryId));
    }

    private QueueEntry requireAccessibleQueueEntry(TenantAccessContext access, String queueEntryId) {
        Objects.requireNonNull(access, "Tenant access context is required");
        String normalizedId = normalizeId(queueEntryId, "Queue entry ID");
        Optional<QueueEntry> accessible = access.isPlatformAdministrator()
                ? queueEntryRepository.findById(normalizedId)
                : access.isOperational()
                ? queueEntryRepository.findByIdAndBusinessId(normalizedId, access.requireBusinessId())
                : queueEntryRepository.findByIdAndUserId(normalizedId, access.userId());
        return accessible.orElseThrow(() -> new ResourceNotFoundException("Queue entry not found"));
    }

    private QueueEntry requireOperationalQueueEntry(TenantAccessContext access, String queueEntryId) {
        if (access.isCustomer()) throw new AccessDeniedException("Operational queue access is required");
        return requireAccessibleQueueEntry(access, queueEntryId);
    }

    private void updateQueueEntry(QueueEntry queueEntry) {
        if (!queueEntryRepository.update(queueEntry)) {
            throw new ResourceNotFoundException("Queue entry not found: " + queueEntry.getQueueEntryId());
        }
    }

    private void updateBooking(Booking booking) {
        if (!bookingRepository.update(booking)) {
            throw new ResourceNotFoundException("Booking not found: " + booking.getBookingId());
        }
    }

    private QueueEntry callWaitingEntry(QueueEntry queueEntry) {
        if (queueEntry.getQueueStatus() != QueueStatus.WAITING) {
            throw new BusinessRuleViolationException("Queue entry cannot be called in current state");
        }
        Booking booking = requireCanonicalBooking(queueEntry);
        CanonicalQueueScope scope = validateCanonicalScopeIntegrity(queueEntry, booking);
        validateOperationalEligibility(scope);
        queueEntry.setBooking(booking);
        LifecycleStateSnapshot.QueueEntryState queueState = LifecycleStateSnapshot.queueEntry(queueEntry);
        try {
            if (!queueEntry.callNext(LocalDateTime.now(clock))) {
                throw new BusinessRuleViolationException("Queue entry cannot be called in current state");
            }
            updateQueueEntry(queueEntry);
            notifyCustomer(queueEntry, "QUEUE_CALLED", "Your vehicle is next in the queue.");
            return snapshotQueueEntry(queueEntry);
        } catch (RuntimeException exception) {
            coordinator.compensate(exception, () -> rollbackQueueCall(exception, queueState));
            throw exception;
        }
    }

    private void rollbackQueueCall(RuntimeException failure, LifecycleStateSnapshot.QueueEntryState queueState) {
        try {
            queueState.restore();
            updateQueueEntry(queueState.queueEntry());
        } catch (RuntimeException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private Booking requireCanonicalBooking(QueueEntry queueEntry) {
        Booking associatedBooking = queueEntry.getBooking();
        if (associatedBooking == null || associatedBooking.getBookingId() == null
                || associatedBooking.getBookingId().isBlank()) {
            throw new BusinessRuleViolationException("Queue entry must have an associated booking");
        }
        String bookingId = associatedBooking.getBookingId();
        Booking canonicalBooking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        if (!bookingId.equals(canonicalBooking.getBookingId())) {
            throw new BusinessRuleViolationException("Queue entry booking association is inconsistent");
        }
        if (canonicalBooking.getQueueEntry() == null
                || !queueEntry.getQueueEntryId().equals(canonicalBooking.getQueueEntry().getQueueEntryId())) {
            throw new BusinessRuleViolationException("Queue entry booking association is inconsistent");
        }
        return canonicalBooking;
    }

    private void rollbackLifecycle(
            RuntimeException failure,
            LifecycleStateSnapshot.BookingState bookingState,
            List<LifecycleStateSnapshot.QueueEntryState> queueStates
    ) {
        try {
            bookingState.restore();
            for (LifecycleStateSnapshot.QueueEntryState queueState : queueStates) {
                queueState.restore();
                updateQueueEntry(queueState.queueEntry());
            }
            updateBooking(bookingState.booking());
        } catch (RuntimeException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private void notifyCustomer(QueueEntry queueEntry, String type, String message) {
        if (notificationManagementService != null && queueEntry.getBooking() != null) {
            notificationManagementService.createNotification(
                    queueEntry.getBooking().getUser(), queueEntry.getBooking(), type, message);
        }
    }

    private void notifyCustomerBestEffort(QueueEntry queueEntry, String type, String message) {
        coordinator.afterCommitBestEffort(
                () -> notifyCustomer(queueEntry, type, message),
                exception -> LOGGER.warn("Unable to create {} notification for queue entry {}",
                        type, queueEntry.getQueueEntryId(), exception));
    }

    private void lockQueueCreation(QueueEntry queueEntry) {
        if (queueEntry == null || queueEntry.getBooking() == null
                || queueEntry.getBooking().getBookingId() == null) return;
        String bookingId = queueEntry.getBooking().getBookingId().trim();
        mutationLock.acquire(MutationLock.booking(bookingId));
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        mutationLock.acquire(java.util.List.of(
                MutationLock.offering(booking.getServiceOfferingId()),
                MutationLock.queueBranch(booking.getBranchId())
        ));
    }

    private void lockQueueLifecycle(QueueEntry queueEntry) {
        Booking booking = queueEntry.getBooking();
        if (booking == null || booking.getBookingId() == null) {
            mutationLock.acquire(MutationLock.queueBranch(queueEntry.getBranchId()));
            return;
        }
        mutationLock.acquire(java.util.List.of(
                MutationLock.booking(booking.getBookingId()),
                MutationLock.offering(queueEntry.getServiceOfferingId()),
                MutationLock.queueBranch(queueEntry.getBranchId())
        ));
    }

    private void validateAndResolveQueueEntry(QueueEntry queueEntry) {
        if (queueEntry == null) {
            throw new BusinessRuleViolationException("Queue entry is required");
        }
        String queueEntryId = normalizeId(queueEntry.getQueueEntryId(), "Queue entry ID");
        if (queueEntry.getBooking() == null) {
            throw new BusinessRuleViolationException("Booking is required");
        }
        String bookingId = normalizeId(queueEntry.getBooking().getBookingId(), "Booking ID");
        if (queueEntry.getService() == null) {
            throw new BusinessRuleViolationException("Service consistency field is required");
        }
        String suppliedServiceId = normalizeId(queueEntry.getService().getServiceId(), "Service ID");
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleViolationException("Only confirmed bookings can join the queue");
        }
        CanonicalQueueScope scope = validateBookingScopeIntegrity(booking);
        validateOperationalEligibility(scope);
        ServiceOfferingSnapshot offering = scope.offering();
        if (!offering.serviceId().equals(suppliedServiceId)) {
            throw new BusinessRuleViolationException("Queue entry service must match booking service offering");
        }
        if (booking.getService() == null
                || !offering.serviceId().equals(booking.getService().getServiceId())) {
            throw new BusinessRuleViolationException("Booking service association is inconsistent with its offering");
        }
        if (queueEntryRepository.existsActiveByBookingId(booking.getBookingId())) {
            throw new BusinessRuleViolationException("Booking already has an active queue entry");
        }
        queueEntry.setQueueEntryId(queueEntryId);
        queueEntry.setBooking(booking);
        queueEntry.setService(booking.getService());
        queueEntry.assignOperationalScope(booking.getBranchId(), booking.getServiceOfferingId());
    }

    private CanonicalQueueScope validateBookingScopeIntegrity(Booking booking) {
        BranchSnapshot branch = requireBranch(booking.getBranchId());
        ServiceOfferingSnapshot offering = requireOffering(booking.getServiceOfferingId());
        if (!branch.branchId().equals(offering.branchId())) {
            throw new BusinessRuleViolationException("Booking offering does not belong to its branch");
        }
        if (booking.getService() == null
                || !offering.serviceId().equals(booking.getService().getServiceId())) {
            throw new BusinessRuleViolationException(
                    "Booking service association is inconsistent with its offering");
        }
        return new CanonicalQueueScope(branch, offering);
    }

    private CanonicalQueueScope validateCanonicalScopeIntegrity(QueueEntry queueEntry, Booking booking) {
        CanonicalQueueScope scope = validateBookingScopeIntegrity(booking);
        if (!scope.branch().branchId().equals(queueEntry.getBranchId())) {
            throw new BusinessRuleViolationException("Queue entry branch does not match its booking");
        }
        if (!scope.offering().offeringId().equals(queueEntry.getServiceOfferingId())) {
            throw new BusinessRuleViolationException("Queue entry offering does not match its booking");
        }
        if (queueEntry.getService() == null
                || !scope.offering().serviceId().equals(queueEntry.getService().getServiceId())) {
            throw new BusinessRuleViolationException(
                    "Queue entry service association is inconsistent with its booking offering");
        }
        return scope;
    }

    private void validateOperationalEligibility(CanonicalQueueScope scope) {
        if (!scope.branch().effectiveActive()) {
            throw new BusinessRuleViolationException(
                    "Inactive branch or owning business cannot accept queue work");
        }
        if (!scope.offering().effectiveActive()) {
            throw new BusinessRuleViolationException(
                    "Inactive service offering or reusable service cannot join the queue");
        }
    }

    private BranchSnapshot requireBranch(String branchId) {
        String normalizedBranchId = normalizeId(branchId, "Branch ID");
        return marketplaceQuery.findBranchOptional(normalizedBranchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + normalizedBranchId));
    }

    private ServiceOfferingSnapshot requireOffering(String offeringId) {
        String normalizedOfferingId = normalizeId(offeringId, "Service offering ID");
        return serviceOfferingQuery.findOfferingOptional(normalizedOfferingId)
                .orElseThrow(() -> new ResourceNotFoundException("Offering not found: " + normalizedOfferingId));
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

    private void initializeNewQueueEntry(QueueEntry queueEntry, int position) {
        queueEntry.setQueueStatus(QueueStatus.WAITING);
        queueEntry.setJoinedAt(LocalDateTime.now(clock));
        queueEntry.setCalledAt(null);
        queueEntry.setStartedAt(null);
        queueEntry.setCompletedAt(null);
        queueEntry.updateQueueMetrics(position, 0);
    }

    private QueueEntrySnapshot snapshot(QueueEntry source) {
        return snapshot(source, canonicalBookingForSnapshot(source.getBooking()));
    }

    private QueueEntrySnapshot snapshot(QueueEntry source, Booking booking) {
        return new QueueEntrySnapshot(
                source.getQueueEntryId(),
                booking == null ? null : booking.getBookingId(),
                booking == null || booking.getUser() == null ? null : booking.getUser().getUserId(),
                source.getBranchId(),
                source.getServiceOfferingId(),
                source.getService() == null ? null : source.getService().getServiceId(),
                booking == null ? null : booking.getScheduledDateTime(),
                source.getPosition(),
                source.getQueueStatus(),
                source.getEstimatedWaitMin(),
                source.getJoinedAt(),
                source.getCalledAt(),
                source.getStartedAt(),
                source.getCompletedAt()
        );
    }

    private QueueEntry snapshotQueueEntry(QueueEntry source) {
        return snapshotQueueEntry(source, canonicalBookingForSnapshot(source.getBooking()));
    }

    private QueueEntry snapshotQueueEntry(QueueEntry source, Booking booking) {
        QueueEntry snapshot = new QueueEntry();
        snapshot.setQueueEntryId(source.getQueueEntryId());
        snapshot.setBooking(snapshotBooking(booking));
        snapshot.setService(snapshotService(source.getService()));
        if (source.getBranchId() != null && source.getServiceOfferingId() != null) {
            snapshot.assignOperationalScope(source.getBranchId(), source.getServiceOfferingId());
        }
        snapshot.setPosition(source.getPosition());
        snapshot.setQueueStatus(source.getQueueStatus());
        snapshot.setJoinedAt(source.getJoinedAt());
        snapshot.setCalledAt(source.getCalledAt());
        snapshot.setStartedAt(source.getStartedAt());
        snapshot.setCompletedAt(source.getCompletedAt());
        snapshot.setEstimatedWaitMin(source.getEstimatedWaitMin());
        return snapshot;
    }

    private List<QueueEntry> snapshotQueueEntries(List<QueueEntry> source) {
        Map<String, Booking> bookings = canonicalBookings(source);
        return source.stream().map(entry -> snapshotQueueEntry(
                entry, bookings.getOrDefault(bookingId(entry), entry.getBooking()))).toList();
    }

    private List<QueueEntrySnapshot> queueEntrySnapshots(List<QueueEntry> source) {
        Map<String, Booking> bookings = canonicalBookings(source);
        return source.stream().map(entry -> snapshot(
                entry, bookings.getOrDefault(bookingId(entry), entry.getBooking()))).toList();
    }

    private Map<String, Booking> canonicalBookings(List<QueueEntry> entries) {
        return bookingRepository.findByIds(entries.stream().map(this::bookingId)
                        .filter(Objects::nonNull).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(Booking::getBookingId,
                        java.util.function.Function.identity()));
    }

    private String bookingId(QueueEntry entry) {
        return entry.getBooking() == null ? null : entry.getBooking().getBookingId();
    }

    private Booking canonicalBookingForSnapshot(Booking associated) {
        if (associated == null || associated.getBookingId() == null) return associated;
        return bookingRepository.findById(associated.getBookingId()).orElse(associated);
    }

    private Booking snapshotBooking(Booking source) {
        if (source == null) return null;
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

    private record CanonicalQueueScope(BranchSnapshot branch, ServiceOfferingSnapshot offering) {
    }
}
