package com.carwash.queue.application;

import com.carwash.queue.application.QueuePolicyProperties;
import com.carwash.catalog.application.ServiceOfferingQuery;
import com.carwash.catalog.application.ServiceOfferingSnapshot;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.queue.domain.QueueEntryRepository;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
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
    private final InMemoryDataCoordinator coordinator;
    private final ServiceOfferingQuery serviceOfferingQuery;

    public QueueOrderingService(QueueEntryRepository queueEntryRepository,
                                InMemoryDataCoordinator coordinator,
                                QueuePolicyProperties queuePolicy,
                                ServiceOfferingQuery serviceOfferingQuery) {
        this.queueEntryRepository = Objects.requireNonNull(queueEntryRepository, "Queue repository is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        Objects.requireNonNull(queuePolicy, "Queue policy is required");
        this.serviceOfferingQuery = Objects.requireNonNull(
                serviceOfferingQuery, "Service offering query is required");
    }

    /** Rebalances each branch independently; retained for internal maintenance compatibility. */
    public void rebalanceActiveQueue() {
        coordinator.write(() -> {
            List<QueueEntry> activeEntries = queueEntryRepository.findActiveOrdered();
            LinkedHashSet<String> branchIds = activeEntries.stream()
                    .map(QueueEntry::getBranchId)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            branchIds.forEach(branchId -> applyQueueMetrics(
                    queueEntryRepository.findActiveOrderedByBranch(branchId)));
            List<QueueEntry> legacyEntries = activeEntries.stream()
                    .filter(entry -> entry.getBranchId() == null)
                    .toList();
            if (!legacyEntries.isEmpty()) applyQueueMetrics(legacyEntries);
        });
    }

    public void rebalanceActiveQueue(String branchId) {
        String normalizedBranchId = requireBranchId(branchId);
        coordinator.write(() -> applyQueueMetrics(
                queueEntryRepository.findActiveOrderedByBranch(normalizedBranchId)));
    }

    public void rebalanceActiveQueue(List<QueueEntry> orderedActiveQueue) {
        coordinator.write(() -> {
            String branchId = orderedActiveQueue.isEmpty() ? null : orderedActiveQueue.getFirst().getBranchId();
            boolean mixedBranches = orderedActiveQueue.stream()
                    .anyMatch(entry -> !Objects.equals(branchId, entry.getBranchId()));
            if (mixedBranches) {
                throw new IllegalArgumentException("Queue rebalancing cannot mix branches");
            }
            applyQueueMetrics(orderedActiveQueue);
        });
    }

    private void applyQueueMetrics(List<QueueEntry> orderedActiveQueue) {
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
            if (!queueEntryRepository.update(queueEntry)) {
                throw new ResourceNotFoundException("Queue entry not found: " + queueEntry.getQueueEntryId());
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

}
