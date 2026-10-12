package com.carwash.queue.infrastructure;

import com.carwash.testsupport.ServiceTestSupport;
import com.carwash.testsupport.TestAccess;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QueueTelemetryTest extends ServiceTestSupport {
    @Test void currentDepthTracksCommittedLifecycleAndDeletionWithoutScans() {
        var booking = createConfirmedBooking();
        var queue = queueService.createQueueEntry(TestAccess.platformAdministrator(), ids.queueEntry(),
                booking.getBookingId(), booking.getService().getServiceId());
        var meters = new SimpleMeterRegistry();
        var sampler = new QueueTelemetrySampler(queueRepository, coordinator, meters);
        assertEquals(0, meters.get("carwash.queue.snapshot.available").gauge().value());
        sampler.refresh();
        assertEquals(1, queueRepository.telemetryState().depth());
        assertEquals(1, meters.get("carwash.queue.depth").gauge().value());
        queueService.callQueueEntry(TestAccess.platformAdministrator(), queue.getQueueEntryId());
        queueService.startService(TestAccess.platformAdministrator(), queue.getQueueEntryId());
        assertEquals(1, queueRepository.telemetryState().depth());
        queueService.completeQueueEntry(TestAccess.platformAdministrator(), queue.getQueueEntryId());
        sampler.refresh();
        assertEquals(0, meters.get("carwash.queue.depth").gauge().value());
        assertEquals(0, meters.get("carwash.queue.estimated.wait").gauge().value());
        assertTrue(meters.get("carwash.queue.snapshot.age").gauge().value() >= 0);
    }
}
