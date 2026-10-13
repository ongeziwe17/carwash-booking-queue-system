package com.carwash.queue.application;

import java.time.LocalDate;

/** Queue lifecycle events grouped in the authorized branch's local calendar. */
public record QueueReportBucket(LocalDate date, int hour, Event event, long count,
                                long sampleCount, long totalSeconds, Long maximumSeconds) {
    public enum Event { JOINED, STARTED, COMPLETED }
}
