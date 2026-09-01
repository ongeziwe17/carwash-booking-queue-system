package com.carwash.queue.application;

import com.carwash.queue.application.QueuePolicyProperties;
import com.carwash.catalog.application.ServiceOfferingQuery;
import com.carwash.catalog.application.ServiceOfferingSnapshot;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.queue.domain.QueueEntryRepository;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.application.MutationLock;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Applies active-queue position and wait invariants independently for each branch.
 */
public final class QueueOrderingService {

    private final QueueEntryRepository queueEntryRepository;
    private final DataTransactionOperations coordinator;
    private final MutationLock mutationLock;
    private final ServiceOfferingQuery serviceOfferingQuery;

    public QueueOrderingService(QueueEntryRepository queueEntryRepository,
                                DataTransactionOperations coordinator,
                                QueuePolicyProperties queuePolicy,
                                ServiceOfferingQuery serviceOfferingQuery) {
        this(queueEntryRepository, coordinator, MutationLock.noOp(), queuePolicy, serviceOfferingQuery);
    }

    public QueueOrderingService(QueueEntryRepository queueEntryRepository,
                                DataTransactionOperations coordinator,
                                MutationLock mutationLock,
                                QueuePolicyProperties queuePolicy,
                                ServiceOfferingQuery serviceOfferingQuery) {
        this.queueEntryRepository = Objects.requireNonNull(queueEntryRepository, "Queue repository is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.mutationLock = Objects.requireNonNull(mutationLock, "Mutation lock is required");
        Objects.requireNonNull(queuePolicy, "Queue policy is required");
        this.serviceOfferingQuery = Objects.requireNonNull(
                serviceOfferingQuery, "Service offering query is required");
    }

    /** Rebalances each branch independently; retained for internal maintenance compatibility. */
    void rebalanceActiveQueue() {
        coordinator.write(() -> {
            List<QueueEntry> activeEntries = queueEntryRepository.findActiveOrdered();
            LinkedHashSet<String> branchIds = activeEntries.stream()
                    .map(QueueEntry::getBranchId)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            mutationLock.acquire(branchIds.stream().map(MutationLock::queueBranch).toList());
            branchIds.forEach(branchId -> applyQueueMetrics(
                    queueEntryRepository.findActiveOrderedByBranch(branchId)));
            List<QueueEntry> legacyEntries = activeEntries.stream()
                    .filter(entry -> entry.getBranchId() == null)
                    .toList();
            if (!legacyEntries.isEmpty()) applyQueueMetrics(legacyEntries);
        });
    }

    void rebalanceActiveQueue(String branchId) {
        String normalizedBranchId = requireBranchId(branchId);
        coordinator.write(() -> {
            mutationLock.acquire(MutationLock.queueBranch(normalizedBranchId));
            applyQueueMetrics(queueEntryRepository.findActiveOrderedByBranch(normalizedBranchId));
        });
    }

    /**
     * Trusted cross-module contract for a branch that was authorized through explicit platform-
     * administrator scope in the enclosing write transaction.
     */
    public void rebalanceActiveQueueForAdministrator(String branchId) {
        rebalanceActiveQueue(branchId);
    }

    /** Rebalances one authorized tenant branch with the tenant predicate retained on every write. */
    public void rebalanceActiveQueueForBusiness(String branchId, String businessId) {
        String normalizedBranchId = requireBranchId(branchId);
        String normalizedBusinessId = requireBusinessId(businessId);
        coordinator.write(() -> {
            mutationLock.acquire(MutationLock.queueBranch(normalizedBranchId));
            applyQueueMetrics(
                    queueEntryRepository.findByBranchIdAndBusinessId(normalizedBranchId, normalizedBusinessId),
                    normalizedBusinessId);
        });
    }

    void rebalanceActiveQueue(List<QueueEntry> orderedActiveQueue) {
        coordinator.write(() -> {
            String branchId = orderedActiveQueue.isEmpty() ? null : orderedActiveQueue.getFirst().getBranchId();
            boolean mixedBranches = orderedActiveQueue.stream()
                    .anyMatch(entry -> !Objects.equals(branchId, entry.getBranchId()));
            if (mixedBranches) {
                throw new IllegalArgumentException("Queue rebalancing cannot mix branches");
            }
            if (branchId != null) mutationLock.acquire(MutationLock.queueBranch(branchId));
            applyQueueMetrics(orderedActiveQueue);
        });
    }

    /**
     * Trusted application contract for an already authorized administrator queue snapshot.
     */
    public void rebalanceActiveQueueForAdministrator(List<QueueEntry> orderedActiveQueue) {
        rebalanceActiveQueue(orderedActiveQueue);
    }

    /** Rebalances an already ordered tenant queue without dropping its write predicate. */
    public void rebalanceActiveQueueForBusiness(
            List<QueueEntry> orderedActiveQueue, String businessId) {
        String normalizedBusinessId = requireBusinessId(businessId);
        coordinator.write(() -> {
            String branchId = orderedActiveQueue.isEmpty() ? null : orderedActiveQueue.getFirst().getBranchId();
            boolean mixedBranches = orderedActiveQueue.stream()
                    .anyMatch(entry -> !Objects.equals(branchId, entry.getBranchId()));
            if (mixedBranches) throw new IllegalArgumentException("Queue rebalancing cannot mix branches");
            if (branchId != null) mutationLock.acquire(MutationLock.queueBranch(branchId));
            applyQueueMetrics(orderedActiveQueue, normalizedBusinessId);
        });
    }

    private void applyQueueMetrics(List<QueueEntry> orderedActiveQueue) {
        applyQueueMetrics(orderedActiveQueue, null);
    }

    private void applyQueueMetrics(List<QueueEntry> orderedActiveQueue, String businessId) {
        List<Integer> estimatedWaits = new ArrayList<>(orderedActiveQueue.size());
        int estimatedWaitMin = 0;
        for (int index = 0; index < orderedActiveQueue.size(); index++) {
            estimatedWaits.add(estimatedWaitMin);
            if (index < orderedActiveQueue.size() - 1) {
                estimatedWaitMin = Math.addExact(
                        estimatedWaitMin, effectiveServiceDurationMinutes(orderedActiveQueue.get(index)));
            }
        }
        for (int index = 0; index < orderedActiveQueue.size(); index++) {
            QueueEntry queueEntry = orderedActiveQueue.get(index);
            queueEntry.updateQueueMetrics(index + 1, estimatedWaits.get(index));
            boolean updated = businessId == null
                    ? queueEntryRepository.updateForAdministrator(queueEntry)
                    : queueEntryRepository.updateForBusiness(queueEntry, businessId);
            if (!updated) {
                throw new ResourceNotFoundException("Queue entry not found");
            }
        }
    }

    private int effectiveServiceDurationMinutes(QueueEntry queueEntry) {
        if (queueEntry.getServiceOfferingId() == null || queueEntry.getServiceOfferingId().isBlank()) {
            throw new BusinessRuleViolationException("Queue entry service offering is required for wait estimation");
        }
        ServiceOfferingSnapshot offering = serviceOfferingQuery
                .findOfferingOptional(queueEntry.getServiceOfferingId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Offering not found: " + queueEntry.getServiceOfferingId()));
        return offering.estimatedDurationMin();
    }

    private String requireBranchId(String branchId) {
        String normalized = branchId == null ? null : branchId.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("Branch ID is required");
        }
        return normalized;
    }

    private String requireBusinessId(String businessId) {
        String normalized = businessId == null ? null : businessId.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("Business ID is required");
        }
        return normalized;
    }

}
