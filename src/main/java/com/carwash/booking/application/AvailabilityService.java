package com.carwash.booking.application;

import com.carwash.booking.domain.Booking;

import com.carwash.booking.api.dto.AvailabilitySlotResponse;
import com.carwash.booking.api.dto.ServiceAvailabilityResponse;
import com.carwash.booking.application.BookingPolicyProperties;
import com.carwash.catalog.domain.Service;
import com.carwash.catalog.domain.ServiceRepository;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class AvailabilityService {

    private final ServiceRepository serviceRepository;
    private final BookingSlotPolicyService slotPolicy;
    private final BookingPolicyProperties bookingPolicy;
    private final DataTransactionOperations coordinator;

    public AvailabilityService(
            ServiceRepository serviceRepository,
            BookingSlotPolicyService slotPolicy,
            BookingPolicyProperties bookingPolicy,
            DataTransactionOperations coordinator
    ) {
        this.serviceRepository = Objects.requireNonNull(serviceRepository, "Service repository is required");
        this.slotPolicy = Objects.requireNonNull(slotPolicy, "Booking slot policy is required");
        this.bookingPolicy = Objects.requireNonNull(bookingPolicy, "Booking policy is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
    }

    public ServiceAvailabilityResponse findAvailability(String serviceId, LocalDate date) {
        return coordinator.read(() -> {
            String canonicalServiceId = requireServiceId(serviceId);
            Service service = serviceRepository.findById(canonicalServiceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + canonicalServiceId));
            if (!service.isActive()) {
                throw new BusinessRuleViolationException("Inactive service cannot be booked");
            }

            List<AvailabilitySlotResponse> slots = slotPolicy.findAvailableSlots(date, service).stream()
                    .map(slot -> new AvailabilitySlotResponse(
                            slot.startDateTime(), slot.estimatedEndDateTime(), slot.capacityRemaining()))
                    .toList();
            return new ServiceAvailabilityResponse(
                    service.getServiceId(),
                    service.getServiceName(),
                    date,
                    service.getEstimatedDurationMin(),
                    bookingPolicy.maxActiveBookingsPerSlot(),
                    slots
            );
        });
    }

    private String requireServiceId(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new BusinessRuleViolationException("Service ID is required");
        }
        return serviceId.trim();
    }

}
