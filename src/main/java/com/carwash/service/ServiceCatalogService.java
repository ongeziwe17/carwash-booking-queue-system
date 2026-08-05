package com.carwash.service;

import com.carwash.domain.Service;
import com.carwash.repository.BookingRepository;
import com.carwash.repository.QueueEntryRepository;
import com.carwash.repository.ServiceRepository;
import com.carwash.repository.inmemory.InMemoryDataCoordinator;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public class ServiceCatalogService {

    private final ServiceRepository serviceRepository;
    private final BookingRepository bookingRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final InMemoryDataCoordinator coordinator;

    public ServiceCatalogService(ServiceRepository serviceRepository) {
        this(serviceRepository, null, null, new InMemoryDataCoordinator());
    }

    public ServiceCatalogService(
            ServiceRepository serviceRepository,
            BookingRepository bookingRepository,
            QueueEntryRepository queueEntryRepository,
            InMemoryDataCoordinator coordinator
    ) {
        this.serviceRepository = Objects.requireNonNull(serviceRepository, "Service repository is required");
        this.bookingRepository = bookingRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
    }

    public Service createService(String serviceId, String serviceName, String description,
                                 BigDecimal price, int estimatedDurationMin) {
        return createService(new Service(serviceId, serviceName, description, price, estimatedDurationMin));
    }

    public Service createService(Service service) {
        return coordinator.write(() -> {
            validateService(service);
            if (!serviceRepository.insert(service)) {
                throw new BusinessRuleViolationException("Service ID already exists");
            }
            return service;
        });
    }

    public Service findById(String serviceId) {
        return coordinator.read(() -> requireService(serviceId));
    }

    public List<Service> findAll() {
        return coordinator.read(serviceRepository::findAll);
    }

    public List<Service> findByActive(boolean active) {
        return coordinator.read(() -> serviceRepository.findAll().stream()
                .filter(service -> service.isActive() == active).toList());
    }

    public Service updateService(String serviceId, String serviceName, String description,
                                 BigDecimal price, int estimatedDurationMin) {
        Service updated = new Service();
        updated.setServiceId(serviceId);
        updated.setServiceName(serviceName);
        updated.setDescription(description);
        updated.setPrice(price);
        updated.setEstimatedDurationMin(estimatedDurationMin);
        return updateService(updated);
    }

    public Service updateService(Service service) {
        return coordinator.write(() -> {
            if (service == null) throw new BusinessRuleViolationException("Service is required");
            Service existing = requireService(service.getServiceId());
            validateService(service);
            existing.updateDetails(service.getServiceName(), service.getDescription(), service.getPrice(),
                    service.getEstimatedDurationMin());
            if (!serviceRepository.update(existing)) {
                throw new ResourceNotFoundException("Service not found: " + existing.getServiceId());
            }
            return existing;
        });
    }

    public Service activateService(String serviceId) {
        return coordinator.write(() -> {
            Service service = requireService(serviceId);
            service.activate();
            if (!serviceRepository.update(service)) throw new ResourceNotFoundException("Service not found: " + serviceId);
            return service;
        });
    }

    public Service deactivateService(String serviceId) {
        return coordinator.write(() -> {
            Service service = requireService(serviceId);
            service.deactivate();
            if (!serviceRepository.update(service)) throw new ResourceNotFoundException("Service not found: " + serviceId);
            return service;
        });
    }

    public void deleteService(String serviceId) {
        coordinator.write(() -> {
            requireService(serviceId);
            boolean referencedByBooking = bookingRepository != null && bookingRepository.existsByServiceId(serviceId);
            boolean referencedByQueue = queueEntryRepository != null && queueEntryRepository.existsByServiceId(serviceId);
            if (referencedByBooking || referencedByQueue) {
                throw new BusinessRuleViolationException("Referenced service cannot be deleted; deactivate it instead");
            }
            if (!serviceRepository.deleteById(serviceId)) throw new ResourceNotFoundException("Service not found: " + serviceId);
        });
    }

    private Service requireService(String serviceId) {
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));
    }

    private void validateService(Service service) {
        if (service == null) throw new BusinessRuleViolationException("Service is required");
        if (isBlank(service.getServiceId())) throw new BusinessRuleViolationException("Service ID must not be blank");
        if (isBlank(service.getServiceName())) throw new BusinessRuleViolationException("Service name must not be blank");
        if (service.getPrice() == null || service.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleViolationException("Price must be zero or positive");
        }
        if (service.getEstimatedDurationMin() <= 0) {
            throw new BusinessRuleViolationException("Estimated duration must be greater than zero");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
