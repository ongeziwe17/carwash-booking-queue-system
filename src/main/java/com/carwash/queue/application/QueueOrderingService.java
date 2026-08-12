package com.carwash.queue.application;

import com.carwash.queue.application.QueuePolicyProperties;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.catalog.domain.Service;
import com.carwash.queue.domain.QueueEntryRepository;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import com.carwash.shared.exception.ResourceNotFoundException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Applies the global active-queue position and wait invariants.
 */
public final class QueueOrderingService {

    private final QueueEntryRepository queueEntryRepository;
    private final InMemoryDataCoordinator coordinator;
    private final QueuePolicyProperties queuePolicy;

    public QueueOrderingService(QueueEntryRepository queueEntryRepository,
                                InMemoryDataCoordinator coordinator,
                                QueuePolicyProperties queuePolicy) {
        this.queueEntryRepository = Objects.requireNonNull(queueEntryRepository, "Queue repository is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.queuePolicy = Objects.requireNonNull(queuePolicy, "Queue policy is required");
    }

    public void rebalanceActiveQueue() {
        coordinator.write(() -> rebalanceActiveQueue(queueEntryRepository.findActiveOrdered()));
    }

    public void rebalanceActiveQueue(List<QueueEntry> orderedActiveQueue) {
        coordinator.write(() -> applyQueueMetrics(orderedActiveQueue));
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
        Service service = queueEntry.getService();
        if (service != null && service.getEstimatedDurationMin() > 0) {
            return service.getEstimatedDurationMin();
        }
        return durationInMinutes(queuePolicy.defaultServiceDuration());
    }

    private int durationInMinutes(Duration duration) {
        long seconds = duration.getSeconds();
        long minutes = seconds / 60;
        if (seconds % 60 != 0 || duration.getNano() != 0) minutes++;
        return Math.toIntExact(Math.max(minutes, 1));
    }
}
