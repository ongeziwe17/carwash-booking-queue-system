package com.carwash.catalog.application;

import com.carwash.catalog.domain.Service;
import com.carwash.catalog.domain.ServiceOffering;
import com.carwash.catalog.domain.ServiceOfferingRepository;
import com.carwash.catalog.domain.ServiceOfferingStatus;
import com.carwash.catalog.domain.ServiceRepository;
import com.carwash.marketplace.application.BranchSnapshot;
import com.carwash.marketplace.application.MarketplaceQuery;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.application.MutationLock;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ServiceOfferingService implements ServiceOfferingQuery {

    private final ServiceOfferingRepository offeringRepository;
    private final ServiceRepository serviceRepository;
    private final MarketplaceQuery marketplaceQuery;
    private final DataTransactionOperations coordinator;
    private final MutationLock mutationLock;
    private final ServiceOfferingCapacityQuery capacityQuery;
    private final Clock clock;

    public ServiceOfferingService(
            ServiceOfferingRepository offeringRepository,
            ServiceRepository serviceRepository,
            MarketplaceQuery marketplaceQuery,
            DataTransactionOperations coordinator,
            Clock clock
    ) {
        this(offeringRepository, serviceRepository, marketplaceQuery, coordinator,
                MutationLock.noOp(), ServiceOfferingCapacityQuery.empty(), clock);
    }

    public ServiceOfferingService(
            ServiceOfferingRepository offeringRepository,
            ServiceRepository serviceRepository,
            MarketplaceQuery marketplaceQuery,
            DataTransactionOperations coordinator,
            MutationLock mutationLock,
            ServiceOfferingCapacityQuery capacityQuery,
            Clock clock
    ) {
        this.offeringRepository = Objects.requireNonNull(offeringRepository, "Offering repository is required");
        this.serviceRepository = Objects.requireNonNull(serviceRepository, "Service repository is required");
        this.marketplaceQuery = Objects.requireNonNull(marketplaceQuery, "Marketplace query is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.mutationLock = Objects.requireNonNull(mutationLock, "Mutation lock is required");
        this.capacityQuery = Objects.requireNonNull(capacityQuery, "Offering capacity query is required");
        this.clock = Objects.requireNonNull(clock, "Application clock is required");
    }

    public ServiceOfferingSnapshot createOffering(String branchId, CreateServiceOfferingCommand command) {
        return coordinator.write(() -> {
            String normalizedBranchId = normalizeId(branchId, "Branch ID");
            if (command == null) {
                throw new BusinessRuleViolationException("Offering request is required");
            }
            BranchSnapshot branch = requireBranch(normalizedBranchId);
            Service service = requireService(command.serviceId());
            String offeringId = normalizeId(command.offeringId(), "Offering ID");
            mutationLock.acquire(MutationLock.offering(offeringId));
            if (offeringRepository.existsById(offeringId)) {
                throw new BusinessRuleViolationException("Offering ID already exists");
            }
            if (offeringRepository.findByBranchIdAndServiceId(normalizedBranchId, service.getServiceId()).isPresent()) {
                throw new BusinessRuleViolationException(
                        "Branch already has an offering for this service; reactivate or update it instead");
            }
            LocalDateTime now = LocalDateTime.now(clock);
            ServiceOffering offering = new ServiceOffering(
                    offeringId,
                    normalizedBranchId,
                    service.getServiceId(),
                    command.price(),
                    command.estimatedDurationMin(),
                    command.concurrentCapacity(),
                    ServiceOfferingStatus.ACTIVE,
                    now,
                    now
            );
            if (!offeringRepository.insert(offering)) {
                throw new BusinessRuleViolationException("Offering ID already exists");
            }
            return snapshot(offering, service, branch);
        });
    }

    public ServiceOfferingSnapshot findOffering(String offeringId) {
        return coordinator.read(() -> {
            ServiceOffering offering = requireOffering(offeringId);
            return snapshot(offering, requireService(offering.getServiceId()), requireBranch(offering.getBranchId()));
        });
    }

    @Override
    public Optional<ServiceOfferingSnapshot> findOfferingOptional(String offeringId) {
        String normalizedId = normalizeId(offeringId, "Offering ID");
        return coordinator.read(() -> offeringRepository.findById(normalizedId)
                .map(offering -> snapshot(
                        offering,
                        requireService(offering.getServiceId()),
                        requireBranch(offering.getBranchId()))));
    }

    @Override
    public List<ServiceOfferingSnapshot> findOfferingsByBranch(String branchId) {
        String normalizedId = normalizeId(branchId, "Branch ID");
        return coordinator.read(() -> {
            BranchSnapshot branch = requireBranch(normalizedId);
            Map<String, Service> services = serviceRepository.findAll().stream()
                    .collect(Collectors.toMap(Service::getServiceId, Function.identity()));
            return offeringRepository.findByBranchId(normalizedId).stream()
                    .map(offering -> snapshot(offering, requireAssociated(
                            services, offering.getServiceId(), "Service"), branch))
                    .toList();
        });
    }

    @Override
    public List<ServiceOfferingSnapshot> findOfferingsByService(String serviceId) {
        String normalizedId = normalizeId(serviceId, "Service ID");
        return coordinator.read(() -> {
            Service service = requireService(normalizedId);
            Map<String, BranchSnapshot> branches = marketplaceQuery.findAllBranches().stream()
                    .collect(Collectors.toMap(BranchSnapshot::branchId, Function.identity()));
            return offeringRepository.findByServiceId(normalizedId).stream()
                    .map(offering -> snapshot(offering, service, requireAssociated(
                            branches, offering.getBranchId(), "Branch")))
                    .toList();
        });
    }

    @Override
    public List<ServiceOfferingSnapshot> findDiscoverableOfferingsByBranch(String branchId) {
        return findOfferingsByBranch(branchId).stream()
                .filter(ServiceOfferingSnapshot::discoverable)
                .toList();
    }

    @Override
    public Optional<ServiceOfferingSnapshot> findOfferingByBranchAndService(String branchId, String serviceId) {
        String normalizedBranchId = normalizeId(branchId, "Branch ID");
        String normalizedServiceId = normalizeId(serviceId, "Service ID");
        return coordinator.read(() -> {
            BranchSnapshot branch = requireBranch(normalizedBranchId);
            Service service = requireService(normalizedServiceId);
            return offeringRepository.findByBranchIdAndServiceId(normalizedBranchId, normalizedServiceId)
                    .map(offering -> snapshot(offering, service, branch));
        });
    }

    public ServiceOfferingSnapshot updateOffering(String offeringId, UpdateServiceOfferingCommand command) {
        return coordinator.write(() -> {
            mutationLock.acquire(MutationLock.offering(offeringId));
            if (command == null) {
                throw new BusinessRuleViolationException("Offering request is required");
            }
            ServiceOffering existing = requireOffering(offeringId);
            Service service = requireService(existing.getServiceId());
            BranchSnapshot branch = requireBranch(existing.getBranchId());
            ServiceOffering updated = existing.updateTerms(
                    command.price(),
                    command.estimatedDurationMin(),
                    command.concurrentCapacity(),
                    LocalDateTime.now(clock)
            );
            int requiredCapacity = capacityQuery.maximumConcurrentActiveBookings(
                    updated.getOfferingId(), updated.getEstimatedDurationMin());
            if (updated.getConcurrentCapacity() < requiredCapacity) {
                throw new BusinessRuleViolationException(
                        "Offering capacity cannot be lower than its active booking overlap");
            }
            updateRecord(updated);
            return snapshot(updated, service, branch);
        });
    }

    public ServiceOfferingSnapshot activateOffering(String offeringId) {
        return changeStatus(offeringId, true);
    }

    public ServiceOfferingSnapshot deactivateOffering(String offeringId) {
        return changeStatus(offeringId, false);
    }

    private ServiceOfferingSnapshot changeStatus(String offeringId, boolean active) {
        return coordinator.write(() -> {
            mutationLock.acquire(MutationLock.offering(offeringId));
            ServiceOffering existing = requireOffering(offeringId);
            Service service = requireService(existing.getServiceId());
            BranchSnapshot branch = requireBranch(existing.getBranchId());
            ServiceOffering updated = active
                    ? existing.activate(LocalDateTime.now(clock))
                    : existing.deactivate(LocalDateTime.now(clock));
            updateRecord(updated);
            return snapshot(updated, service, branch);
        });
    }

    private ServiceOfferingSnapshot snapshot(
            ServiceOffering offering,
            Service service,
            BranchSnapshot branch
    ) {
        boolean effectiveActive = offering.isActive() && service.isActive() && branch.effectiveActive();
        return new ServiceOfferingSnapshot(
                offering.getOfferingId(),
                offering.getBranchId(),
                offering.getServiceId(),
                service.getServiceName(),
                service.getDescription(),
                offering.getPrice(),
                offering.getEstimatedDurationMin(),
                offering.getConcurrentCapacity(),
                offering.getStatus(),
                effectiveActive,
                effectiveActive && branch.publicDiscoveryEnabled(),
                offering.getCreatedAt(),
                offering.getUpdatedAt()
        );
    }

    private BranchSnapshot requireBranch(String branchId) {
        return marketplaceQuery.findBranchOptional(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId));
    }

    private Service requireService(String serviceId) {
        String normalizedId = normalizeId(serviceId, "Service ID");
        return serviceRepository.findById(normalizedId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + normalizedId));
    }

    private ServiceOffering requireOffering(String offeringId) {
        String normalizedId = normalizeId(offeringId, "Offering ID");
        return offeringRepository.findById(normalizedId)
                .orElseThrow(() -> new ResourceNotFoundException("Offering not found: " + normalizedId));
    }

    private void updateRecord(ServiceOffering offering) {
        if (!offeringRepository.update(offering)) {
            throw new ResourceNotFoundException("Offering not found: " + offering.getOfferingId());
        }
    }

    private <T> T requireAssociated(Map<String, T> values, String id, String association) {
        return Optional.ofNullable(values.get(id))
                .orElseThrow(() -> new IllegalStateException(
                        association + " is missing for offering association: " + id));
    }

    private String normalizeId(String value, String field) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new BusinessRuleViolationException(field + " must not be blank");
        }
        if (normalized.length() > ServiceOffering.MAX_ID_LENGTH) {
            throw new BusinessRuleViolationException(
                    field + " must not exceed " + ServiceOffering.MAX_ID_LENGTH + " characters");
        }
        return normalized;
    }
}
