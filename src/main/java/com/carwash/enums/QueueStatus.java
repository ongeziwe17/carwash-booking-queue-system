package com.carwash.enums;

public enum QueueStatus {
    WAITING,
    CALLED,
    IN_PROGRESS,
    COMPLETED,
    EXITED;

    public boolean isActive() {
        return this == WAITING || this == CALLED || this == IN_PROGRESS;
    }
}
