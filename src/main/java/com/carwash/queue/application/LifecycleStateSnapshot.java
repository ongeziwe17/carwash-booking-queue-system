package com.carwash.queue.application;

import com.carwash.booking.domain.Booking;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.booking.domain.BookingStatus;
import com.carwash.queue.domain.QueueStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Focused mutable-state snapshots used to roll back coordinated in-memory
 * booking and queue lifecycle mutations.
 */
public final class LifecycleStateSnapshot {

    private LifecycleStateSnapshot() {
    }

    public static BookingState booking(Booking booking) {
        return new BookingState(booking, booking.getStatus(), booking.getQueueEntry());
    }

    public static List<QueueEntryState> queueEntries(List<QueueEntry> queueEntries) {
        return queueEntries.stream().map(LifecycleStateSnapshot::queueEntry).toList();
    }

    public static QueueEntryState queueEntry(QueueEntry queueEntry) {
        return new QueueEntryState(
                queueEntry,
                queueEntry.getBooking(),
                queueEntry.getQueueStatus(),
                queueEntry.getPosition(),
                queueEntry.getEstimatedWaitMin(),
                queueEntry.getCalledAt(),
                queueEntry.getStartedAt(),
                queueEntry.getCompletedAt()
        );
    }

    public record BookingState(Booking booking, BookingStatus status, QueueEntry queueEntry) {

        public void restore() {
            booking.setStatus(status);
            QueueEntry current = booking.getQueueEntry();
            if (current != null && queueEntry == null) {
                booking.detachQueueEntry(current.getQueueEntryId());
            } else if (queueEntry != null) {
                if (current != null && !queueEntry.getQueueEntryId().equals(current.getQueueEntryId())) {
                    booking.detachQueueEntry(current.getQueueEntryId());
                }
                booking.attachQueueEntry(queueEntry);
            }
        }
    }

    public record QueueEntryState(
            QueueEntry queueEntry,
            Booking booking,
            QueueStatus status,
            int position,
            int estimatedWaitMin,
            LocalDateTime calledAt,
            LocalDateTime startedAt,
            LocalDateTime completedAt
    ) {

        public void restore() {
            queueEntry.setBooking(booking);
            queueEntry.setQueueStatus(status);
            queueEntry.setPosition(position);
            queueEntry.setEstimatedWaitMin(estimatedWaitMin);
            queueEntry.setCalledAt(calledAt);
            queueEntry.setStartedAt(startedAt);
            queueEntry.setCompletedAt(completedAt);
        }
    }
}
