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
import com.carwash.access.application.TenantAccessContext;

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

    ServiceOfferingSnapshot createOffering(String branchId, CreateServiceOfferingCommand command) {
        return coordinator.write(() -> createOfferingInside(null, branchId, command));
    }

    public ServiceOfferingSnapshot createOffering(
            TenantAccessContext access,
            String branchId,
            CreateServiceOfferingCommand command
    ) {
        return coordinator.write(() -> createOfferingInside(access, branchId, command));
    }

    public ServiceOfferingSnapshot findOffering(String offeringId) {
        return coordinator.read(() -> {
            ServiceOffering offering = requireOffering(offeringId);
            return snapshot(offering, requireService(offering.getServiceId()), requireBranch(offering.getBranchId()));
        });
    }

    public ServiceOfferingSnapshot findOffering(TenantAccessContext access, String offeringId) {
        if (access.isPlatformAdministrator()) return findOffering(offeringId);
        String tenantId = access.requireBusinessId();
        return coordinator.read(() -> {
            ServiceOffering offering = offeringRepository.findByIdAndBusinessId(
                            normalizeId(offeringId, "Offering ID"), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Offering not found"));
            return snapshot(offering, requireService(offering.getServiceId()),
                    requireTenantBranch(offering.getBranchId(), tenantId));
        });
    }

    public List<ServiceOfferingSnapshot> findOfferingsByBranch(
            TenantAccessContext access,
            String branchId
    ) {
        if (access.isPlatformAdministrator()) return findOfferingsByBranch(branchId);
        String normalizedId = normalizeId(branchId, "Branch ID");
        String tenantId = access.requireBusinessId();
        return coordinator.read(() -> {
            BranchSnapshot branch = requireTenantBranch(normalizedId, tenantId);
            Map<String, Service> services = serviceRepository.findAll().stream()
                    .collect(Collectors.toMap(Service::getServiceId, Function.identity()));
            return offeringRepository.findByBranchIdAndBusinessId(normalizedId, tenantId).stream()
                    .map(offering -> snapshot(offering, requireAssociated(
                            services, offering.getServiceId(), "Service"), branch))
                    .toList();
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

    ServiceOfferingSnapshot updateOffering(String offeringId, UpdateServiceOfferingCommand command) {
        return coordinator.write(() -> updateOfferingInside(null, offeringId, command));
    }

    public ServiceOfferingSnapshot updateOffering(
            TenantAccessContext access,
            String offeringId,
            UpdateServiceOfferingCommand command
    ) {
        return coordinator.write(() -> updateOfferingInside(access, offeringId, command));
    }

    ServiceOfferingSnapshot activateOffering(String offeringId) {
        return changeStatus(offeringId, true);
    }

    public ServiceOfferingSnapshot activateOffering(TenantAccessContext access, String offeringId) {
        return coordinator.write(() -> changeStatusInside(access, offeringId, true));
    }

    ServiceOfferingSnapshot deactivateOffering(String offeringId) {
        return changeStatus(offeringId, false);
    }

    public ServiceOfferingSnapshot deactivateOffering(TenantAccessContext access, String offeringId) {
        return coordinator.write(() -> changeStatusInside(access, offeringId, false));
    }

    private ServiceOfferingSnapshot changeStatus(String offeringId, boolean active) {
        return coordinator.write(() -> changeStatusInside(null, offeringId, active));
    }

    private ServiceOfferingSnapshot createOfferingInside(
            TenantAccessContext access, String branchId, CreateServiceOfferingCommand command) {
        if (command == null) throw new BusinessRuleViolationException("Offering request is required");
        String normalizedBranchId = normalizeId(branchId, "Branch ID");
        String offeringId = normalizeId(command.offeringId(), "Offering ID");
        mutationLock.acquire(List.of(
                MutationLock.offering(offeringId), MutationLock.branch(normalizedBranchId)));
        BranchSnapshot branch = requireBranchForMutation(access, normalizedBranchId);
        mutationLock.acquire(MutationLock.business(branch.businessId()));
        Service service = requireService(command.serviceId());
        if (offeringRepository.existsById(offeringId)) {
            throw new BusinessRuleViolationException("Offering ID already exists");
        }
        if (offeringRepository.findByBranchIdAndServiceId(normalizedBranchId, service.getServiceId()).isPresent()) {
            throw new BusinessRuleViolationException(
                    "Branch already has an offering for this service; reactivate or update it instead");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        ServiceOffering offering = new ServiceOffering(
                offeringId, normalizedBranchId, service.getServiceId(), command.price(),
                command.estimatedDurationMin(), command.concurrentCapacity(),
                ServiceOfferingStatus.ACTIVE, now, now);
        if (!offeringRepository.insert(offering)) {
            throw new BusinessRuleViolationException("Offering ID already exists");
        }
        return snapshot(offering, service, branch);
    }

    private ServiceOfferingSnapshot updateOfferingInside(
            TenantAccessContext access, String offeringId, UpdateServiceOfferingCommand command) {
        String normalizedOfferingId = normalizeId(offeringId, "Offering ID");
        mutationLock.acquire(MutationLock.offering(normalizedOfferingId));
        if (command == null) throw new BusinessRuleViolationException("Offering request is required");
        ServiceOffering existing = requireOfferingForMutation(access, normalizedOfferingId);
        mutationLock.acquire(MutationLock.branch(existing.getBranchId()));
        Service service = requireService(existing.getServiceId());
        BranchSnapshot branch = requireBranchForMutation(access, existing.getBranchId());
        mutationLock.acquire(MutationLock.business(branch.businessId()));
        ServiceOffering updated = existing.updateTerms(
                command.price(), command.estimatedDurationMin(), command.concurrentCapacity(),
                LocalDateTime.now(clock));
        int requiredCapacity = capacityQuery.maximumConcurrentActiveBookings(
                updated.getOfferingId(), updated.getEstimatedDurationMin());
        if (updated.getConcurrentCapacity() < requiredCapacity) {
            throw new BusinessRuleViolationException(
                    "Offering capacity cannot be lower than its active booking overlap");
        }
        updateRecord(access, updated);
        return snapshot(updated, service, branch);
    }

    private ServiceOfferingSnapshot changeStatusInside(
            TenantAccessContext access, String offeringId, boolean active) {
        String normalizedOfferingId = normalizeId(offeringId, "Offering ID");
        mutationLock.acquire(MutationLock.offering(normalizedOfferingId));
        ServiceOffering existing = requireOfferingForMutation(access, normalizedOfferingId);
        mutationLock.acquire(MutationLock.branch(existing.getBranchId()));
        Service service = requireService(existing.getServiceId());
        BranchSnapshot branch = requireBranchForMutation(access, existing.getBranchId());
        mutationLock.acquire(MutationLock.business(branch.businessId()));
        ServiceOffering updated = active
                ? existing.activate(LocalDateTime.now(clock))
                : existing.deactivate(LocalDateTime.now(clock));
        updateRecord(access, updated);
        return snapshot(updated, service, branch);
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

    private BranchSnapshot requireTenantBranch(String branchId, String businessId) {
        return marketplaceQuery.findBranchOptionalByBusiness(branchId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
    }

    private void requireAccessibleBranch(TenantAccessContext access, String branchId) {
        Objects.requireNonNull(access, "Tenant access context is required");
        if (!access.isPlatformAdministrator()) {
            requireTenantBranch(normalizeId(branchId, "Branch ID"), access.requireBusinessId());
        }
    }

    private void requireAccessibleOffering(TenantAccessContext access, String offeringId) {
        Objects.requireNonNull(access, "Tenant access context is required");
        if (!access.isPlatformAdministrator()) {
            offeringRepository.findByIdAndBusinessId(
                            normalizeId(offeringId, "Offering ID"), access.requireBusinessId())
                    .orElseThrow(() -> new ResourceNotFoundException("Offering not found"));
        }
    }

    private BranchSnapshot requireBranchForMutation(TenantAccessContext access, String branchId) {
        Objects.requireNonNull(branchId, "Branch ID is required");
        return access == null || access.isPlatformAdministrator()
                ? requireBranch(branchId)
                : requireTenantBranch(branchId, access.requireBusinessId());
    }

    private ServiceOffering requireOfferingForMutation(TenantAccessContext access, String offeringId) {
        Optional<ServiceOffering> offering = access == null || access.isPlatformAdministrator()
                ? offeringRepository.findById(offeringId)
                : offeringRepository.findByIdAndBusinessId(offeringId, access.requireBusinessId());
        return offering.orElseThrow(() -> new ResourceNotFoundException("Offering not found"));
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

    private void updateRecord(TenantAccessContext access, ServiceOffering offering) {
        boolean updated = access == null || access.isPlatformAdministrator()
                ? offeringRepository.updateForAdministrator(offering)
                : offeringRepository.updateForBusiness(offering, access.requireBusinessId());
        if (!updated) {
            throw new ResourceNotFoundException("Offering not found");
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
