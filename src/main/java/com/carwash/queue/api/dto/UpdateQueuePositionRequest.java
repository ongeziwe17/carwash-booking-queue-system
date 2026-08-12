package com.carwash.queue.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateQueuePositionRequest(@NotNull @Positive Integer position) {
}
