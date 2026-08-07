package com.carwash.service;

import com.carwash.domain.Service;
import com.carwash.repository.ServiceRepository;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.util.List;

public class ServiceCatalogService {

    private final ServiceRepository serviceRepository;


    public ServiceCatalogService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    public Service createService(
            String serviceId,
            String serviceName,
            String description,
            BigDecimal price,
            int estimatedDurationMin
    ) {
        return createService(new Service(serviceId, serviceName, description, price, estimatedDurationMin));
    }

    public Service createService(Service service) {
        validateService(service);
        serviceRepository.save(service);
        return service;
    }

    public Service findById(String serviceId) {
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));
    }

    public List<Service> findAll() {
        return serviceRepository.findAll();
    }

    public List<Service> findByActive(boolean active) {
        return serviceRepository.findAll().stream()
                .filter(service -> service.isActive() == active).toList();
    }

    public Service updateService(
            String serviceId,
            String serviceName,
            String description,
            BigDecimal price,
            int estimatedDurationMin
    ) {
        Service existing = findById(serviceId);
        Service updated = new Service();
        updated.setServiceId(serviceId);
        updated.setServiceName(serviceName);
        updated.setDescription(description);
        updated.setPrice(price);
        updated.setEstimatedDurationMin(estimatedDurationMin);
        validateService(updated);
        existing.updateDetails(serviceName, description, price, estimatedDurationMin);
        serviceRepository.save(existing);
        return existing;
    }

    public Service updateService(Service service) {
        Service existing = findById(service.getServiceId());
        validateService(service);
        existing.updateDetails(service.getServiceName(), service.getDescription(), service.getPrice(), service.getEstimatedDurationMin());
        serviceRepository.save(existing);
        return existing;
    }

    public Service activateService(String serviceId) {
        Service service = findById(serviceId);
        service.activate();
        serviceRepository.save(service);
        return service;
    }

    public Service deactivateService(String serviceId) {
        Service service = findById(serviceId);
        service.deactivate();
        serviceRepository.save(service);
        return service;
    }

    public void deleteService(String serviceId) {
        findById(serviceId);
        serviceRepository.delete(serviceId);
    }

    private void validateService(Service service) {
        if (service == null) throw new BusinessRuleViolationException("Service is required");
        if (isBlank(service.getServiceName())) throw new BusinessRuleViolationException("Service name must not be blank");
        if (service.getPrice() == null || service.getPrice().compareTo(BigDecimal.ZERO) < 0) throw new BusinessRuleViolationException("Price must be zero or positive");
        if (service.getEstimatedDurationMin() <= 0) throw new BusinessRuleViolationException("Estimated duration must be greater than zero");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
