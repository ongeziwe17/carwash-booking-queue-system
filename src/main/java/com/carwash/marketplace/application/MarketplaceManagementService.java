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
import com.carwash.access.application.TenantAccessContext;
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
    private final Clock clock;

    public MarketplaceManagementService(
            CarWashBusinessRepository businessRepository,
            CarWashBranchRepository branchRepository,
            DataTransactionOperations coordinator,
            Clock clock
    ) {
        this.businessRepository = Objects.requireNonNull(businessRepository, "Business repository is required");
        this.branchRepository = Objects.requireNonNull(branchRepository, "Branch repository is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.clock = Objects.requireNonNull(clock, "Application clock is required");
    }

    public BusinessSnapshot registerBusiness(RegisterBusinessCommand command) {
        return coordinator.write(() -> {
            validateBusiness(command);
            String businessId = normalizeRequiredId(command.businessId(), "Business ID");
            if (businessRepository.findById(businessId).isPresent()) {
                throw new BusinessRuleViolationException("Business ID already exists");
            }
            rejectDuplicateRegistrationNumber(command.registrationNumber(), null);
            LocalDateTime now = LocalDateTime.now(clock);
            CarWashBusiness business = new CarWashBusiness(
                    command.businessId(),
                    command.businessName(),
                    command.contactEmail(),
                    command.contactPhone(),
                    command.registrationNumber(),
                    BusinessStatus.ACTIVE,
                    now,
                    now
            );
            if (!businessRepository.insert(business)) {
                throw new BusinessRuleViolationException("Business ID already exists");
            }
            return BusinessSnapshot.from(business);
        });
    }

    public BusinessSnapshot registerBusiness(TenantAccessContext access, RegisterBusinessCommand command) {
        requirePlatformAdministrator(access);
        return registerBusiness(command);
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
        requireAccessibleBusiness(access, businessId);
        return updateBusiness(businessId, command);
    }

    public BusinessSnapshot activateBusiness(TenantAccessContext access, String businessId) {
        requireAccessibleBusiness(access, businessId);
        return activateBusiness(businessId);
    }

    public BusinessSnapshot deactivateBusiness(TenantAccessContext access, String businessId) {
        requireAccessibleBusiness(access, businessId);
        return deactivateBusiness(businessId);
    }

    public BranchSnapshot createBranch(
            TenantAccessContext access,
            String businessId,
            CreateBranchCommand command
    ) {
        requireAccessibleBusiness(access, businessId);
        return createBranch(businessId, command);
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
        requireAccessibleBranch(access, branchId);
        return updateBranch(branchId, command);
    }

    public BranchSnapshot activateBranch(TenantAccessContext access, String branchId) {
        requireAccessibleBranch(access, branchId);
        return activateBranch(branchId);
    }

    public BranchSnapshot deactivateBranch(TenantAccessContext access, String branchId) {
        requireAccessibleBranch(access, branchId);
        return deactivateBranch(branchId);
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

    public BusinessSnapshot updateBusiness(String businessId, UpdateBusinessCommand command) {
        return coordinator.write(() -> {
            CarWashBusiness existing = requireBusiness(businessId);
            validateBusiness(command);
            rejectDuplicateRegistrationNumber(command.registrationNumber(), existing.getBusinessId());
            CarWashBusiness updated = existing.updateDetails(
                    command.businessName(),
                    command.contactEmail(),
                    command.contactPhone(),
                    command.registrationNumber(),
                    LocalDateTime.now(clock)
            );
            updateBusinessRecord(updated);
            return BusinessSnapshot.from(updated);
        });
    }

    public BusinessSnapshot activateBusiness(String businessId) {
        return changeBusinessStatus(businessId, true);
    }

    public BusinessSnapshot deactivateBusiness(String businessId) {
        return changeBusinessStatus(businessId, false);
    }

    public BranchSnapshot createBranch(String businessId, CreateBranchCommand command) {
        return coordinator.write(() -> {
            CarWashBusiness business = requireBusiness(businessId);
            validateBranch(command);
            LocalDateTime now = LocalDateTime.now(clock);
            CarWashBranch branch = new CarWashBranch(
                    command.branchId(),
                    business.getBusinessId(),
                    command.branchName(),
                    command.addressLine1(),
                    command.addressLine2(),
                    command.city(),
                    command.province(),
                    command.postalCode(),
                    command.countryCode(),
                    command.latitude(),
                    command.longitude(),
                    canonicalTimezone(command.timezone()),
                    BranchStatus.ACTIVE,
                    command.publicDiscoveryEnabled(),
                    now,
                    now
            );
            if (!branchRepository.insert(branch)) {
                throw new BusinessRuleViolationException("Branch ID already exists");
            }
            return BranchSnapshot.from(branch, business);
        });
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

    public BranchSnapshot updateBranch(String branchId, UpdateBranchCommand command) {
        return coordinator.write(() -> {
            CarWashBranch existing = requireBranch(branchId);
            validateBranch(command);
            CarWashBranch updated = existing.updateDetails(
                    command.branchName(),
                    command.addressLine1(),
                    command.addressLine2(),
                    command.city(),
                    command.province(),
                    command.postalCode(),
                    command.countryCode(),
                    command.latitude(),
                    command.longitude(),
                    canonicalTimezone(command.timezone()),
                    command.publicDiscoveryEnabled(),
                    LocalDateTime.now(clock)
            );
            updateBranchRecord(updated);
            return snapshot(updated);
        });
    }

    public BranchSnapshot activateBranch(String branchId) {
        return changeBranchStatus(branchId, true);
    }

    public BranchSnapshot deactivateBranch(String branchId) {
        return changeBranchStatus(branchId, false);
    }

    @Override
    public List<BranchSnapshot> findDiscoverableBranches() {
        return findAllBranches().stream()
                .filter(BranchSnapshot::discoverable)
                .toList();
    }

    private BusinessSnapshot changeBusinessStatus(String businessId, boolean active) {
        return coordinator.write(() -> {
            CarWashBusiness existing = requireBusiness(businessId);
            CarWashBusiness updated = active
                    ? existing.activate(LocalDateTime.now(clock))
                    : existing.deactivate(LocalDateTime.now(clock));
            updateBusinessRecord(updated);
            return BusinessSnapshot.from(updated);
        });
    }

    private BranchSnapshot changeBranchStatus(String branchId, boolean active) {
        return coordinator.write(() -> {
            CarWashBranch existing = requireBranch(branchId);
            CarWashBranch updated = active
                    ? existing.activate(LocalDateTime.now(clock))
                    : existing.deactivate(LocalDateTime.now(clock));
            updateBranchRecord(updated);
            return snapshot(updated);
        });
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

    private void updateBusinessRecord(CarWashBusiness business) {
        if (!businessRepository.update(business)) {
            throw new ResourceNotFoundException("Business not found: " + business.getBusinessId());
        }
    }

    private void updateBranchRecord(CarWashBranch branch) {
        if (!branchRepository.update(branch)) {
            throw new ResourceNotFoundException("Branch not found: " + branch.getBranchId());
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
