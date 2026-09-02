package com.carwash.marketplace.application;

import com.carwash.marketplace.domain.BranchStatus;
import com.carwash.marketplace.domain.BusinessStatus;
import com.carwash.marketplace.domain.CarWashBranch;
import com.carwash.marketplace.domain.CarWashBranchRepository;
import com.carwash.marketplace.domain.CarWashBusiness;
import com.carwash.marketplace.domain.CarWashBusinessRepository;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.application.MutationLock;
import com.carwash.access.application.TenantAccessContext;
import com.carwash.audit.application.AuditActor;
import com.carwash.audit.application.AuditCommand;
import com.carwash.audit.application.AuditOperations;
import com.carwash.audit.domain.AuditAction;
import com.carwash.audit.domain.AuditSource;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class MarketplaceManagementService implements MarketplaceQuery {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+() .-]{7,32}$");
    private static final Pattern COUNTRY_CODE = Pattern.compile("^[A-Z]{2}$");
    private static final BigDecimal MIN_LATITUDE = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MIN_LONGITUDE = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    private final CarWashBusinessRepository businessRepository;
    private final CarWashBranchRepository branchRepository;
    private final DataTransactionOperations coordinator;
    private final MutationLock mutationLock;
    private final Clock clock;
    private final AuditOperations audit;

    public MarketplaceManagementService(
            CarWashBusinessRepository businessRepository,
            CarWashBranchRepository branchRepository,
            DataTransactionOperations coordinator,
            Clock clock
    ) {
        this(businessRepository, branchRepository, coordinator, MutationLock.noOp(), clock, AuditOperations.noOp());
    }

    public MarketplaceManagementService(
            CarWashBusinessRepository businessRepository,
            CarWashBranchRepository branchRepository,
            DataTransactionOperations coordinator,
            MutationLock mutationLock,
            Clock clock
    ) {
        this(businessRepository, branchRepository, coordinator, mutationLock, clock, AuditOperations.noOp());
    }

    public MarketplaceManagementService(
            CarWashBusinessRepository businessRepository,
            CarWashBranchRepository branchRepository,
            DataTransactionOperations coordinator,
            MutationLock mutationLock,
            Clock clock,
            AuditOperations audit
    ) {
        this.businessRepository = Objects.requireNonNull(businessRepository, "Business repository is required");
        this.branchRepository = Objects.requireNonNull(branchRepository, "Branch repository is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.mutationLock = Objects.requireNonNull(mutationLock, "Mutation lock is required");
        this.clock = Objects.requireNonNull(clock, "Application clock is required");
        this.audit = Objects.requireNonNull(audit, "Audit operations are required");
    }

    BusinessSnapshot registerBusiness(RegisterBusinessCommand command) {
        return coordinator.write(() -> registerBusinessInside(command));
    }

    public BusinessSnapshot registerBusiness(TenantAccessContext access, RegisterBusinessCommand command) {
        return audit.execute(eventForBusiness(access, AuditAction.BUSINESS_REGISTERED,
                command == null ? null : command.businessId(), "BUSINESS",
                command == null ? null : command.businessId()), () -> coordinator.write(() -> {
            requirePlatformAdministrator(access);
            return registerBusinessInside(command);
        }));
    }

    public List<BusinessSnapshot> findAccessibleBusinesses(TenantAccessContext access) {
        Objects.requireNonNull(access, "Tenant access context is required");
        if (access.isPlatformAdministrator()) return findAllBusinesses();
        String tenantId = access.requireBusinessId();
        return coordinator.read(() -> businessRepository.findByIdAndTenantId(tenantId, tenantId)
                .map(BusinessSnapshot::from).stream().toList());
    }

    public BusinessSnapshot findBusiness(TenantAccessContext access, String businessId) {
        if (access.isPlatformAdministrator()) return findBusiness(businessId);
        return BusinessSnapshot.from(requireTenantBusiness(businessId, access.requireBusinessId()));
    }

    public BusinessSnapshot updateBusiness(
            TenantAccessContext access,
            String businessId,
            UpdateBusinessCommand command
    ) {
        return audit.execute(event(access, AuditAction.BUSINESS_UPDATED, "BUSINESS", businessId),
                () -> coordinator.write(() -> updateBusinessInside(access, businessId, command)));
    }

    public BusinessSnapshot activateBusiness(TenantAccessContext access, String businessId) {
        return audit.execute(event(access, AuditAction.BUSINESS_ACTIVATED, "BUSINESS", businessId),
                () -> coordinator.write(() -> changeBusinessStatusInside(access, businessId, true)));
    }

    public BusinessSnapshot deactivateBusiness(TenantAccessContext access, String businessId) {
        return audit.execute(event(access, AuditAction.BUSINESS_DEACTIVATED, "BUSINESS", businessId),
                () -> coordinator.write(() -> changeBusinessStatusInside(access, businessId, false)));
    }

    public BranchSnapshot createBranch(
            TenantAccessContext access,
            String businessId,
            CreateBranchCommand command
    ) {
        return audit.execute(eventForBusiness(access, AuditAction.BRANCH_CREATED, businessId, "BRANCH",
                        command == null ? null : command.branchId()),
                () -> coordinator.write(() -> createBranchInside(access, businessId, command)));
    }

    public List<BranchSnapshot> findBranchesByBusiness(TenantAccessContext access, String businessId) {
        requireAccessibleBusiness(access, businessId);
        return findBranchesByBusiness(businessId);
    }

    public BranchSnapshot findBranch(TenantAccessContext access, String branchId) {
        if (access.isPlatformAdministrator()) return findBranch(branchId);
        CarWashBranch branch = requireTenantBranch(branchId, access.requireBusinessId());
        return coordinator.read(() -> snapshot(branch));
    }

    public BranchSnapshot updateBranch(
            TenantAccessContext access,
            String branchId,
            UpdateBranchCommand command
    ) {
        return audit.execute(event(access, AuditAction.BRANCH_UPDATED, "BRANCH", branchId),
                () -> coordinator.write(() -> updateBranchInside(access, branchId, command)));
    }

    public BranchSnapshot activateBranch(TenantAccessContext access, String branchId) {
        return audit.execute(event(access, AuditAction.BRANCH_ACTIVATED, "BRANCH", branchId),
                () -> coordinator.write(() -> changeBranchStatusInside(access, branchId, true)));
    }

    public BranchSnapshot deactivateBranch(TenantAccessContext access, String branchId) {
        return audit.execute(event(access, AuditAction.BRANCH_DEACTIVATED, "BRANCH", branchId),
                () -> coordinator.write(() -> changeBranchStatusInside(access, branchId, false)));
    }

    private AuditCommand event(TenantAccessContext access, AuditAction action, String targetType, String targetId) {
        String businessId = access != null && access.isPlatformAdministrator() ? null : access.businessId();
        return eventForBusiness(access, action, businessId, targetType, targetId);
    }

    private AuditCommand eventForBusiness(TenantAccessContext access, AuditAction action, String businessId,
                                          String targetType, String targetId) {
        Objects.requireNonNull(access, "Tenant access context is required");
        AuditActor actor = AuditActor.user(access.userId(), access.canonicalRoleName(), access.businessId());
        String eventBusiness = access.isPlatformAdministrator() ? safeId(businessId) : access.businessId();
        return AuditCommand.actionForBusiness(action, actor, eventBusiness, targetType, safeId(targetId), AuditSource.API);
    }

    private static String safeId(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() || normalized.length() > 64 ? null : normalized;
    }

    @Override
    public List<BusinessSnapshot> findAllBusinesses() {
        return coordinator.read(() -> businessRepository.findAll().stream()
                .map(BusinessSnapshot::from)
                .toList());
    }

    @Override
    public List<BranchSnapshot> findAllBranches() {
        return coordinator.read(() -> {
            java.util.Map<String, CarWashBusiness> businesses = businessRepository.findAll().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            CarWashBusiness::getBusinessId, java.util.function.Function.identity()));
            return branchRepository.findAll().stream()
                    .map(branch -> BranchSnapshot.from(branch, java.util.Objects.requireNonNull(
                            businesses.get(branch.getBusinessId()), "Branch owner is missing")))
                    .toList();
        });
    }

    public BusinessSnapshot findBusiness(String businessId) {
        return coordinator.read(() -> BusinessSnapshot.from(requireBusiness(businessId)));
    }

    @Override
    public Optional<BusinessSnapshot> findBusinessOptional(String businessId) {
        String normalizedId = normalizeRequiredId(businessId, "Business ID");
        return coordinator.read(() -> businessRepository.findById(normalizedId).map(BusinessSnapshot::from));
    }

    BusinessSnapshot updateBusiness(String businessId, UpdateBusinessCommand command) {
        return coordinator.write(() -> updateBusinessInside(null, businessId, command));
    }

    BusinessSnapshot activateBusiness(String businessId) {
        return changeBusinessStatus(businessId, true);
    }

    BusinessSnapshot deactivateBusiness(String businessId) {
        return changeBusinessStatus(businessId, false);
    }

    BranchSnapshot createBranch(String businessId, CreateBranchCommand command) {
        return coordinator.write(() -> createBranchInside(null, businessId, command));
    }

    @Override
    public List<BranchSnapshot> findBranchesByBusiness(String businessId) {
        return coordinator.read(() -> {
            CarWashBusiness business = requireBusiness(businessId);
            return branchRepository.findByBusinessId(business.getBusinessId()).stream()
                    .map(branch -> BranchSnapshot.from(branch, business))
                    .toList();
        });
    }

    public BranchSnapshot findBranch(String branchId) {
        return coordinator.read(() -> snapshot(requireBranch(branchId)));
    }

    @Override
    public Optional<BranchSnapshot> findBranchOptional(String branchId) {
        String normalizedId = normalizeRequiredId(branchId, "Branch ID");
        return coordinator.read(() -> branchRepository.findById(normalizedId).map(this::snapshot));
    }

    @Override
    public Optional<BranchSnapshot> findBranchOptionalByBusiness(String branchId, String businessId) {
        String normalizedId = normalizeRequiredId(branchId, "Branch ID");
        return coordinator.read(() -> branchRepository.findByIdAndBusinessId(normalizedId, businessId)
                .map(this::snapshot));
    }

    BranchSnapshot updateBranch(String branchId, UpdateBranchCommand command) {
        return coordinator.write(() -> updateBranchInside(null, branchId, command));
    }

    BranchSnapshot activateBranch(String branchId) {
        return changeBranchStatus(branchId, true);
    }

    BranchSnapshot deactivateBranch(String branchId) {
        return changeBranchStatus(branchId, false);
    }

    @Override
    public List<BranchSnapshot> findDiscoverableBranches() {
        return findAllBranches().stream()
                .filter(BranchSnapshot::discoverable)
                .toList();
    }

    private BusinessSnapshot changeBusinessStatus(String businessId, boolean active) {
        return coordinator.write(() -> changeBusinessStatusInside(null, businessId, active));
    }

    private BranchSnapshot changeBranchStatus(String branchId, boolean active) {
        return coordinator.write(() -> changeBranchStatusInside(null, branchId, active));
    }

    private BusinessSnapshot registerBusinessInside(RegisterBusinessCommand command) {
        validateBusiness(command);
        String businessId = normalizeRequiredId(command.businessId(), "Business ID");
        mutationLock.acquire(MutationLock.business(businessId));
        if (businessRepository.findById(businessId).isPresent()) {
            throw new BusinessRuleViolationException("Business ID already exists");
        }
        rejectDuplicateRegistrationNumber(command.registrationNumber(), null);
        LocalDateTime now = LocalDateTime.now(clock);
        CarWashBusiness business = new CarWashBusiness(
                businessId, command.businessName(), command.contactEmail(), command.contactPhone(),
                command.registrationNumber(), BusinessStatus.ACTIVE, now, now);
        if (!businessRepository.insert(business)) {
            throw new BusinessRuleViolationException("Business ID already exists");
        }
        return BusinessSnapshot.from(business);
    }

    private BusinessSnapshot updateBusinessInside(
            TenantAccessContext access, String businessId, UpdateBusinessCommand command) {
        String normalizedId = normalizeRequiredId(businessId, "Business ID");
        mutationLock.acquire(MutationLock.business(normalizedId));
        CarWashBusiness existing = requireBusinessForMutation(access, normalizedId);
        validateBusiness(command);
        rejectDuplicateRegistrationNumber(command.registrationNumber(), existing.getBusinessId());
        CarWashBusiness updated = existing.updateDetails(
                command.businessName(), command.contactEmail(), command.contactPhone(),
                command.registrationNumber(), LocalDateTime.now(clock));
        updateBusinessRecord(access, updated);
        return BusinessSnapshot.from(updated);
    }

    private BusinessSnapshot changeBusinessStatusInside(
            TenantAccessContext access, String businessId, boolean active) {
        String normalizedId = normalizeRequiredId(businessId, "Business ID");
        mutationLock.acquire(MutationLock.business(normalizedId));
        CarWashBusiness existing = requireBusinessForMutation(access, normalizedId);
        CarWashBusiness updated = active
                ? existing.activate(LocalDateTime.now(clock))
                : existing.deactivate(LocalDateTime.now(clock));
        updateBusinessRecord(access, updated);
        return BusinessSnapshot.from(updated);
    }

    private BranchSnapshot createBranchInside(
            TenantAccessContext access, String businessId, CreateBranchCommand command) {
        validateBranch(command);
        String normalizedBusinessId = normalizeRequiredId(businessId, "Business ID");
        String branchId = normalizeRequiredId(command.branchId(), "Branch ID");
        mutationLock.acquire(List.of(
                MutationLock.branch(branchId), MutationLock.business(normalizedBusinessId)));
        CarWashBusiness business = requireBusinessForMutation(access, normalizedBusinessId);
        LocalDateTime now = LocalDateTime.now(clock);
        CarWashBranch branch = new CarWashBranch(
                branchId, business.getBusinessId(), command.branchName(), command.addressLine1(),
                command.addressLine2(), command.city(), command.province(), command.postalCode(),
                command.countryCode(), command.latitude(), command.longitude(), canonicalTimezone(command.timezone()),
                BranchStatus.ACTIVE, command.publicDiscoveryEnabled(), now, now);
        if (!branchRepository.insert(branch)) {
            throw new BusinessRuleViolationException("Branch ID already exists");
        }
        return BranchSnapshot.from(branch, business);
    }

    private BranchSnapshot updateBranchInside(
            TenantAccessContext access, String branchId, UpdateBranchCommand command) {
        String normalizedId = normalizeRequiredId(branchId, "Branch ID");
        mutationLock.acquire(MutationLock.branch(normalizedId));
        CarWashBranch existing = requireBranchForMutation(access, normalizedId);
        mutationLock.acquire(MutationLock.business(existing.getBusinessId()));
        validateBranch(command);
        CarWashBranch updated = existing.updateDetails(
                command.branchName(), command.addressLine1(), command.addressLine2(), command.city(),
                command.province(), command.postalCode(), command.countryCode(), command.latitude(),
                command.longitude(), canonicalTimezone(command.timezone()), command.publicDiscoveryEnabled(),
                LocalDateTime.now(clock));
        updateBranchRecord(access, updated);
        return BranchSnapshot.from(updated, requireBusinessForMutation(access, updated.getBusinessId()));
    }

    private BranchSnapshot changeBranchStatusInside(
            TenantAccessContext access, String branchId, boolean active) {
        String normalizedId = normalizeRequiredId(branchId, "Branch ID");
        mutationLock.acquire(MutationLock.branch(normalizedId));
        CarWashBranch existing = requireBranchForMutation(access, normalizedId);
        mutationLock.acquire(MutationLock.business(existing.getBusinessId()));
        CarWashBranch updated = active
                ? existing.activate(LocalDateTime.now(clock))
                : existing.deactivate(LocalDateTime.now(clock));
        updateBranchRecord(access, updated);
        return BranchSnapshot.from(updated, requireBusinessForMutation(access, updated.getBusinessId()));
    }

    private BranchSnapshot snapshot(CarWashBranch branch) {
        return BranchSnapshot.from(branch, requireBusiness(branch.getBusinessId()));
    }

    private CarWashBusiness requireBusiness(String businessId) {
        String normalizedId = normalizeRequiredId(businessId, "Business ID");
        return businessRepository.findById(normalizedId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + normalizedId));
    }

    private CarWashBusiness requireTenantBusiness(String businessId, String tenantId) {
        String normalizedId = normalizeRequiredId(businessId, "Business ID");
        return coordinator.read(() -> businessRepository.findByIdAndTenantId(normalizedId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found")));
    }

    private CarWashBranch requireBranch(String branchId) {
        String normalizedId = normalizeRequiredId(branchId, "Branch ID");
        return branchRepository.findById(normalizedId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + normalizedId));
    }

    private CarWashBranch requireTenantBranch(String branchId, String tenantId) {
        String normalizedId = normalizeRequiredId(branchId, "Branch ID");
        return coordinator.read(() -> branchRepository.findByIdAndBusinessId(normalizedId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found")));
    }

    private void requireAccessibleBusiness(TenantAccessContext access, String businessId) {
        Objects.requireNonNull(access, "Tenant access context is required");
        if (!access.isPlatformAdministrator()) requireTenantBusiness(businessId, access.requireBusinessId());
    }

    private void requireAccessibleBranch(TenantAccessContext access, String branchId) {
        Objects.requireNonNull(access, "Tenant access context is required");
        if (!access.isPlatformAdministrator()) requireTenantBranch(branchId, access.requireBusinessId());
    }

    private void requirePlatformAdministrator(TenantAccessContext access) {
        Objects.requireNonNull(access, "Tenant access context is required");
        if (!access.isPlatformAdministrator()) {
            throw new AccessDeniedException("Platform administrator access is required");
        }
    }

    private CarWashBusiness requireBusinessForMutation(TenantAccessContext access, String businessId) {
        Optional<CarWashBusiness> business = access == null || access.isPlatformAdministrator()
                ? businessRepository.findById(businessId)
                : businessRepository.findByIdAndTenantId(businessId, access.requireBusinessId());
        return business.orElseThrow(() -> new ResourceNotFoundException("Business not found"));
    }

    private CarWashBranch requireBranchForMutation(TenantAccessContext access, String branchId) {
        Optional<CarWashBranch> branch = access == null || access.isPlatformAdministrator()
                ? branchRepository.findById(branchId)
                : branchRepository.findByIdAndBusinessId(branchId, access.requireBusinessId());
        return branch.orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
    }

    private void updateBusinessRecord(TenantAccessContext access, CarWashBusiness business) {
        boolean updated = access == null || access.isPlatformAdministrator()
                ? businessRepository.updateForAdministrator(business)
                : businessRepository.updateForTenant(business, access.requireBusinessId());
        if (!updated) {
            throw new ResourceNotFoundException("Business not found");
        }
    }

    private void updateBranchRecord(TenantAccessContext access, CarWashBranch branch) {
        boolean updated = access == null || access.isPlatformAdministrator()
                ? branchRepository.updateForAdministrator(branch)
                : branchRepository.updateForBusiness(branch, access.requireBusinessId());
        if (!updated) {
            throw new ResourceNotFoundException("Branch not found");
        }
    }

    private void rejectDuplicateRegistrationNumber(String registrationNumber, String excludedBusinessId) {
        if (businessRepository.existsByRegistrationNumberIgnoreCase(
                registrationNumber, excludedBusinessId)) {
            throw new BusinessRuleViolationException("Business registration number already exists");
        }
    }

    private void validateBusiness(RegisterBusinessCommand command) {
        if (command == null) {
            throw new BusinessRuleViolationException("Business request is required");
        }
        validateId(command.businessId(), "Business ID");
        validateBusinessFields(command.businessName(), command.contactEmail(), command.contactPhone(),
                command.registrationNumber());
    }

    private void validateBusiness(UpdateBusinessCommand command) {
        if (command == null) {
            throw new BusinessRuleViolationException("Business request is required");
        }
        validateBusinessFields(command.businessName(), command.contactEmail(), command.contactPhone(),
                command.registrationNumber());
    }

    private void validateBusinessFields(
            String businessName,
            String contactEmail,
            String contactPhone,
            String registrationNumber
    ) {
        validateRequiredText(businessName, "Business name", 120);
        validateRequiredText(contactEmail, "Contact email", 254);
        if (!EMAIL.matcher(contactEmail).matches()) {
            throw new BusinessRuleViolationException("Contact email must be valid");
        }
        validateRequiredText(contactPhone, "Contact phone", 32);
        if (!PHONE.matcher(contactPhone).matches()) {
            throw new BusinessRuleViolationException("Contact phone must be valid");
        }
        validateOptionalText(registrationNumber, "Registration number", 64);
    }

    private void validateBranch(CreateBranchCommand command) {
        if (command == null) {
            throw new BusinessRuleViolationException("Branch request is required");
        }
        validateId(command.branchId(), "Branch ID");
        validateBranchFields(command.branchName(), command.addressLine1(), command.addressLine2(), command.city(),
                command.province(), command.postalCode(), command.countryCode(), command.latitude(),
                command.longitude(), command.timezone());
    }

    private void validateBranch(UpdateBranchCommand command) {
        if (command == null) {
            throw new BusinessRuleViolationException("Branch request is required");
        }
        validateBranchFields(command.branchName(), command.addressLine1(), command.addressLine2(), command.city(),
                command.province(), command.postalCode(), command.countryCode(), command.latitude(),
                command.longitude(), command.timezone());
    }

    private void validateBranchFields(
            String branchName,
            String addressLine1,
            String addressLine2,
            String city,
            String province,
            String postalCode,
            String countryCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String timezone
    ) {
        validateRequiredText(branchName, "Branch name", 120);
        validateRequiredText(addressLine1, "Address line 1", 200);
        validateOptionalText(addressLine2, "Address line 2", 200);
        validateRequiredText(city, "City", 120);
        validateRequiredText(province, "Province", 120);
        validateRequiredText(postalCode, "Postal code", 20);
        validateRequiredText(countryCode, "Country code", 2);
        if (!COUNTRY_CODE.matcher(countryCode).matches()) {
            throw new BusinessRuleViolationException("Country code must contain two letters");
        }
        validateCoordinate(latitude, MIN_LATITUDE, MAX_LATITUDE, "Latitude");
        validateCoordinate(longitude, MIN_LONGITUDE, MAX_LONGITUDE, "Longitude");
        validateRequiredText(timezone, "Timezone", 64);
        canonicalTimezone(timezone);
    }

    private void validateCoordinate(BigDecimal value, BigDecimal minimum, BigDecimal maximum, String field) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new BusinessRuleViolationException(field + " is outside the valid range");
        }
        if (Math.max(0, value.stripTrailingZeros().scale()) > 8) {
            throw new BusinessRuleViolationException(field + " must use at most 8 decimal places");
        }
    }

    private String canonicalTimezone(String timezone) {
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException exception) {
            throw new BusinessRuleViolationException("Timezone identifier is invalid");
        }
    }

    private String normalizeRequiredId(String id, String field) {
        String normalized = id == null ? null : id.trim();
        validateId(normalized, field);
        return normalized;
    }

    private void validateId(String value, String field) {
        validateRequiredText(value, field, 64);
    }

    private void validateRequiredText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException(field + " must not be blank");
        }
        if (value.length() > maximumLength) {
            throw new BusinessRuleViolationException(field + " must not exceed " + maximumLength + " characters");
        }
    }

    private void validateOptionalText(String value, String field, int maximumLength) {
        if (value != null && value.length() > maximumLength) {
            throw new BusinessRuleViolationException(field + " must not exceed " + maximumLength + " characters");
        }
    }
}
