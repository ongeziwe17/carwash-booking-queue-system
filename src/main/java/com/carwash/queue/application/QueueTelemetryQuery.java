package com.carwash.queue.application;

/** Scalar aggregate only; no tenant/resource identifiers cross this boundary. */
public interface QueueTelemetryQuery {
    State telemetryState();
    record State(long depth, double estimatedWaitSeconds) { }
}
