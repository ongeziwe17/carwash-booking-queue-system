package com.carwash.catalog.application;

import com.carwash.catalog.domain.Service;
import com.carwash.catalog.domain.ServiceOfferingRepository;
import com.carwash.catalog.domain.ServiceRepository;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.access.application.TenantAccessContext;
import com.carwash.audit.application.*;
import com.carwash.audit.domain.AuditAction;
import com.carwash.audit.domain.AuditSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ServiceCatalogService implements ServiceDefinitionQuery {

    private final ServiceRepository serviceRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final ServiceDefinitionUsageQuery serviceUsageQuery;
    private final DataTransactionOperations coordinator;
    private final AuditOperations audit;


    public ServiceCatalogService(
            ServiceRepository serviceRepository,
            ServiceOfferingRepository serviceOfferingRepository,
            ServiceDefinitionUsageQuery serviceUsageQuery,
            DataTransactionOperations coordinator
    ) {
        this(serviceRepository, serviceOfferingRepository, serviceUsageQuery, coordinator, AuditOperations.noOp());
    }

    public ServiceCatalogService(
            ServiceRepository serviceRepository,
            ServiceOfferingRepository serviceOfferingRepository,
            ServiceDefinitionUsageQuery serviceUsageQuery,
            DataTransactionOperations coordinator,
            AuditOperations audit
    ) {
        this.serviceRepository = Objects.requireNonNull(serviceRepository, "Service repository is required");
        this.serviceOfferingRepository = Objects.requireNonNull(
                serviceOfferingRepository, "Service offering repository is required");
        this.serviceUsageQuery = Objects.requireNonNull(serviceUsageQuery, "Service usage query is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.audit = Objects.requireNonNull(audit, "Audit operations are required");
    }

    public Service createService(TenantAccessContext access, String serviceId, String serviceName,
                                 String description, BigDecimal price, int estimatedDurationMin) {
        return audit.execute(event(access, AuditAction.SERVICE_DEFINITION_CREATED, serviceId),
                () -> {
                    access.requirePlatformAdministrator();
                    return createService(serviceId, serviceName, description, price, estimatedDurationMin);
                });
    }

    public Service updateService(TenantAccessContext access, String serviceId, String serviceName,
                                 String description, BigDecimal price, int estimatedDurationMin) {
        return audit.execute(event(access, AuditAction.SERVICE_DEFINITION_UPDATED, serviceId),
                () -> {
                    access.requirePlatformAdministrator();
                    return updateService(serviceId, serviceName, description, price, estimatedDurationMin);
                });
    }

    public Service activateService(TenantAccessContext access, String serviceId) {
        return audit.execute(event(access, AuditAction.SERVICE_DEFINITION_ACTIVATED, serviceId),
                () -> {
                    access.requirePlatformAdministrator();
                    return activateService(serviceId);
                });
    }

    public Service deactivateService(TenantAccessContext access, String serviceId) {
        return audit.execute(event(access, AuditAction.SERVICE_DEFINITION_DEACTIVATED, serviceId),
                () -> {
                    access.requirePlatformAdministrator();
                    return deactivateService(serviceId);
                });
    }

    public void deleteService(TenantAccessContext access, String serviceId) {
        audit.execute(event(access, AuditAction.SERVICE_DEFINITION_DELETED, serviceId),
                () -> {
                    access.requirePlatformAdministrator();
                    deleteService(serviceId);
                });
    }

    private AuditCommand event(TenantAccessContext access, AuditAction action, String serviceId) {
        AuditActor actor = AuditActor.user(access.userId(), access.role().name(), null);
        return AuditCommand.action(action, actor, "SERVICE_DEFINITION", safeId(serviceId), AuditSource.API);
    }

    private static String safeId(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() || normalized.length() > 64 ? null : normalized;
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

    @Override
    public Optional<ServiceDefinitionSnapshot> findServiceDefinitionOptional(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new BusinessRuleViolationException("Service ID must not be blank");
        }
        String normalizedId = serviceId.trim();
        return coordinator.read(() -> serviceRepository.findById(normalizedId).map(this::snapshot));
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
            boolean referencedByBooking = serviceUsageQuery.referencedByBooking(serviceId);
            boolean referencedByQueue = serviceUsageQuery.referencedByQueue(serviceId);
            boolean referencedByOffering = serviceOfferingRepository.existsByServiceId(serviceId);
            if (referencedByOffering) {
                throw new BusinessRuleViolationException(
                        "Service referenced by a branch offering cannot be deleted; deactivate it instead");
            }
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

    private ServiceDefinitionSnapshot snapshot(Service service) {
        return new ServiceDefinitionSnapshot(
                service.getServiceId(),
                service.getServiceName(),
                service.getDescription(),
                service.getPrice(),
                service.getEstimatedDurationMin(),
                service.isActive(),
                service.getCreatedAt()
        );
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
