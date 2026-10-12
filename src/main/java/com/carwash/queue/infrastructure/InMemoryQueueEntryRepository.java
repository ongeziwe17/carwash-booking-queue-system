package com.carwash.queue.infrastructure;

import com.carwash.shared.infrastructure.InMemoryRepository;

import com.carwash.queue.domain.QueueEntry;
import com.carwash.queue.domain.QueueStatus;
import com.carwash.queue.domain.QueueEntryRepository;
import com.carwash.marketplace.application.MarketplaceQuery;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class InMemoryQueueEntryRepository extends InMemoryRepository<QueueEntry, String>
        implements QueueEntryRepository, com.carwash.queue.application.QueueTelemetryQuery {
    @Override public com.carwash.shared.application.PageResult<QueueEntry> findPage(com.carwash.shared.application.ListQuery query) {
        var scope = query.scope();
        java.util.List<QueueEntry> source = scope.businessId() == null ? findMatching(v -> true) : findByBusinessId(scope.businessId());
        source = source.stream().filter(v -> scope.branchId() == null || scope.branchId().equals(v.getBranchId())).toList();
        return query.select(source, java.util.Map.ofEntries(java.util.Map.entry("id", (java.util.function.Function<QueueEntry, Object>) v -> v.getQueueEntryId()),
                java.util.Map.entry("branchId", (java.util.function.Function<QueueEntry, Object>) v -> v.getBranchId()),
                java.util.Map.entry("activeRank", (java.util.function.Function<QueueEntry, Object>) v -> (v.getQueueStatus().isActive() ? 0 : 1)),
                java.util.Map.entry("position", (java.util.function.Function<QueueEntry, Object>) v -> v.getPosition()),
                java.util.Map.entry("joinedAt", (java.util.function.Function<QueueEntry, Object>) v -> v.getJoinedAt()),
                java.util.Map.entry("status", (java.util.function.Function<QueueEntry, Object>) v -> v.getQueueStatus()),
                java.util.Map.entry("serviceId", (java.util.function.Function<QueueEntry, Object>) v -> v.getService().getServiceId()),
                java.util.Map.entry("offeringId", (java.util.function.Function<QueueEntry, Object>) v -> v.getServiceOfferingId())));
    }


    private final java.util.Map<String, Integer> activeWaits = new java.util.HashMap<>();
    private long waitMinutes;

    @Override protected void stored(String id, QueueEntry entry) {
        Integer previous = activeWaits.remove(id);
        if (previous != null) waitMinutes -= previous;
        if (entry != null && entry.getQueueStatus().isActive()) {
            int wait = Math.max(0, entry.getEstimatedWaitMin());
            activeWaits.put(id, wait);
            waitMinutes += wait;
        }
    }

    @Override public State telemetryState() {
        return readAtomically(() -> new State(activeWaits.size(),
                activeWaits.isEmpty() ? 0 : 60.0 * waitMinutes / activeWaits.size()));
    }

    private final MarketplaceQuery marketplace;

    public InMemoryQueueEntryRepository() {
        this(null);
    }

    public InMemoryQueueEntryRepository(MarketplaceQuery marketplace) {
        this.marketplace = marketplace;
    }

    private static final Comparator<QueueEntry> QUEUE_ORDER = Comparator
            .comparing(QueueEntry::getBranchId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing((QueueEntry queueEntry) -> !isActive(queueEntry))
            .thenComparingInt(QueueEntry::getPosition)
            .thenComparing(QueueEntry::getJoinedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(QueueEntry::getQueueEntryId, Comparator.nullsLast(Comparator.naturalOrder()));

    @Override
    public List<QueueEntry> findAllOrdered() {
        return findMatching(queueEntry -> true).stream()
                .sorted(QUEUE_ORDER)
                .toList();
    }

    @Override
    public List<QueueEntry> findActiveOrdered() {
        return findMatching(InMemoryQueueEntryRepository::isActive).stream()
                .sorted(QUEUE_ORDER)
                .toList();
    }

    @Override
    public List<QueueEntry> findActiveOrderedByBranch(String branchId) {
        return findMatching(queueEntry -> isActive(queueEntry)
                        && branchId != null && branchId.equals(queueEntry.getBranchId())).stream()
                .sorted(QUEUE_ORDER)
                .toList();
    }

    @Override
    public Optional<QueueEntry> findNextWaiting() {
        return findActiveOrdered().stream()
                .filter(queueEntry -> queueEntry.getQueueStatus() == QueueStatus.WAITING)
                .findFirst();
    }

    @Override
    public Optional<QueueEntry> findNextWaitingByBranch(String branchId) {
        return findActiveOrderedByBranch(branchId).stream()
                .filter(queueEntry -> queueEntry.getQueueStatus() == QueueStatus.WAITING)
                .findFirst();
    }

    @Override
    public List<QueueEntry> findByBookingId(String bookingId) {
        return findMatching(queueEntry -> queueEntry.getBooking() != null
                && bookingId != null
                && bookingId.equals(queueEntry.getBooking().getBookingId()));
    }

    @Override
    public List<QueueEntry> findByServiceId(String serviceId) {
        return findMatching(queueEntry -> queueEntry.getService() != null
                && serviceId != null
                && serviceId.equals(queueEntry.getService().getServiceId())).stream()
                .sorted(QUEUE_ORDER)
                .toList();
    }

    @Override
    public List<QueueEntry> findByBranchId(String branchId) {
        return findMatching(queueEntry -> branchId != null && branchId.equals(queueEntry.getBranchId())).stream()
                .sorted(QUEUE_ORDER)
                .toList();
    }

    @Override
    public List<QueueEntry> findByBusinessId(String businessId) {
        return findMatching(entry -> branchBelongsTo(entry.getBranchId(), businessId)).stream()
                .sorted(QUEUE_ORDER).toList();
    }

    @Override
    public List<QueueEntry> findByBranchIdAndBusinessId(String branchId, String businessId) {
        if (!branchBelongsTo(branchId, businessId)) return List.of();
        return findByBranchId(branchId);
    }

    @Override
    public Optional<QueueEntry> findByIdAndBusinessId(String queueEntryId, String businessId) {
        return findById(queueEntryId).filter(entry -> branchBelongsTo(entry.getBranchId(), businessId));
    }

    @Override
    public Optional<QueueEntry> findByIdAndUserId(String queueEntryId, String userId) {
        return findById(queueEntryId).filter(entry -> entry.getBooking() != null
                && entry.getBooking().getUser() != null
                && userId.equals(entry.getBooking().getUser().getUserId()));
    }

    @Override
    public Optional<QueueEntry> findNextWaitingByBranchIdAndBusinessId(String branchId, String businessId) {
        if (!branchBelongsTo(branchId, businessId)) return Optional.empty();
        return findNextWaitingByBranch(branchId);
    }

    @Override
    public boolean updateForBusiness(QueueEntry queueEntry, String businessId) {
        return updateMatching(queueEntry.getQueueEntryId(), queueEntry,
                current -> branchBelongsTo(current.getBranchId(), businessId));
    }

    @Override
    public boolean updateForUser(QueueEntry queueEntry, String userId) {
        return updateMatching(queueEntry.getQueueEntryId(), queueEntry,
                current -> current.getBooking() != null && current.getBooking().getUser() != null
                        && userId.equals(current.getBooking().getUser().getUserId()));
    }

    @Override
    public boolean updateForAdministrator(QueueEntry queueEntry) {
        return updateMatching(queueEntry.getQueueEntryId(), queueEntry, current -> true);
    }

    @Override
    public boolean deleteForBusiness(String queueEntryId, String businessId) {
        return deleteMatching(queueEntryId, current -> branchBelongsTo(current.getBranchId(), businessId));
    }

    @Override
    public boolean deleteForUser(String queueEntryId, String userId) {
        return deleteMatching(queueEntryId,
                current -> current.getBooking() != null && current.getBooking().getUser() != null
                        && userId.equals(current.getBooking().getUser().getUserId()));
    }

    @Override
    public boolean deleteForAdministrator(String queueEntryId) {
        return deleteMatching(queueEntryId, current -> true);
    }

    @Override
    public boolean existsByBookingId(String bookingId) {
        return anyMatch(queueEntry -> queueEntry.getBooking() != null
                && bookingId != null
                && bookingId.equals(queueEntry.getBooking().getBookingId()));
    }

    @Override
    public boolean existsActiveByBookingId(String bookingId) {
        if (bookingId == null) return false;
        return anyMatch(queueEntry -> queueEntry.getBooking() != null
                && bookingId.equals(queueEntry.getBooking().getBookingId())
                && queueEntry.getQueueStatus() != null
                && queueEntry.getQueueStatus().isActive());
    }

    @Override
    public boolean existsByServiceId(String serviceId) {
        return anyMatch(queueEntry -> queueEntry.getService() != null
                && serviceId != null
                && serviceId.equals(queueEntry.getService().getServiceId()));
    }

    @Override
    public boolean existsByServiceOfferingId(String serviceOfferingId) {
        return anyMatch(queueEntry -> serviceOfferingId != null
                && serviceOfferingId.equals(queueEntry.getServiceOfferingId()));
    }

    @Override
    protected String getId(QueueEntry entity) {
        return entity.getQueueEntryId();
    }

    private static boolean isActive(QueueEntry queueEntry) {
        return queueEntry.getQueueStatus() != null && queueEntry.getQueueStatus().isActive();
    }

    private boolean branchBelongsTo(String branchId, String businessId) {
        return marketplace != null && marketplace.findBranchOptional(branchId)
                .filter(branch -> businessId.equals(branch.businessId())).isPresent();
    }
}
