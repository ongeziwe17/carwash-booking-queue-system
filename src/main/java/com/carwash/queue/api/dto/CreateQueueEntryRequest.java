package com.carwash.queue.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateQueueEntryRequest(
        @NotBlank @Size(max = 64) String queueEntryId,
        @NotBlank @Size(max = 64) String bookingId,
        @NotBlank @Size(max = 64) String serviceId
) {
    public CreateQueueEntryRequest {
        queueEntryId = trim(queueEntryId);
        bookingId = trim(bookingId);
        serviceId = trim(serviceId);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
