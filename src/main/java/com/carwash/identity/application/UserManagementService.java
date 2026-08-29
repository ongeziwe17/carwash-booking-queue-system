package com.carwash.identity.application;

import com.carwash.identity.domain.User;
import com.carwash.identity.domain.AccountStatus;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.notification.domain.NotificationRepository;
import com.carwash.identity.domain.UserRepository;
import com.carwash.identity.domain.TenantMembership;
import com.carwash.identity.domain.TenantMembershipRepository;
import com.carwash.vehicle.domain.VehicleRepository;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.application.MutationLock;
import com.carwash.identity.domain.RoleCatalog;
import com.carwash.identity.domain.RoleName;
import com.carwash.identity.application.CreateUserCommand;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class UserManagementService implements UserQuery {

    private final UserRepository userRepository;
    private final CredentialService credentialService;
    private final VehicleRepository vehicleRepository;
    private final BookingRepository bookingRepository;
    private final NotificationRepository notificationRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final DataTransactionOperations coordinator;
    private final MutationLock mutationLock;


    public UserManagementService(
            UserRepository userRepository,
            CredentialService credentialService,
            VehicleRepository vehicleRepository,
            BookingRepository bookingRepository,
            NotificationRepository notificationRepository,
            DataTransactionOperations coordinator
    ) {
        this(userRepository, credentialService, vehicleRepository, bookingRepository,
                notificationRepository, null, coordinator, MutationLock.noOp());
    }

    public UserManagementService(
            UserRepository userRepository,
            CredentialService credentialService,
            VehicleRepository vehicleRepository,
            BookingRepository bookingRepository,
            NotificationRepository notificationRepository,
            DataTransactionOperations coordinator,
            MutationLock mutationLock
    ) {
        this(userRepository, credentialService, vehicleRepository, bookingRepository,
                notificationRepository, null, coordinator, mutationLock);
    }

    public UserManagementService(
            UserRepository userRepository,
            CredentialService credentialService,
            VehicleRepository vehicleRepository,
            BookingRepository bookingRepository,
            NotificationRepository notificationRepository,
            TenantMembershipRepository tenantMembershipRepository,
            DataTransactionOperations coordinator,
            MutationLock mutationLock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "User repository is required");
        this.credentialService = Objects.requireNonNull(credentialService, "Credential service is required");
        this.vehicleRepository = vehicleRepository;
        this.bookingRepository = bookingRepository;
        this.notificationRepository = notificationRepository;
        this.tenantMembershipRepository = tenantMembershipRepository;
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.mutationLock = Objects.requireNonNull(mutationLock, "Mutation lock is required");
    }

    public User createUser(CreateUserCommand command) {
        return coordinator.write(() -> {
            validateNewUser(command);
            String userId = command.userId().trim();
            String fullName = command.fullName().trim();
            String email = normalizeEmail(command.email());
            String phone = command.phone().trim();
            credentialService.validatePolicy(command.rawPassword());
            userRepository.findByEmail(email).ifPresent(existing -> {
                throw new BusinessRuleViolationException("User email already exists");
            });
            String encodedPassword = credentialService.encode(command.rawPassword());
            User user = User.withEncodedPassword(userId, fullName, email, phone, encodedPassword,
                    RoleCatalog.role(RoleName.CUSTOMER));
            user.registerAccount();
            if (!userRepository.insert(user)) {
                throw new BusinessRuleViolationException("User ID already exists");
            }
            return user;
        });
    }

    public User findById(String userId) {
        return coordinator.read(() -> requireUser(userId));
    }

    @Override
    public Optional<User> findOptionalById(String userId) {
        return coordinator.read(() -> userRepository.findById(userId));
    }

    public List<User> findAll() {
        return coordinator.read(userRepository::findAll);
    }

    public User updateUser(User user) {
        if (user == null) throw new BusinessRuleViolationException("User is required");
        return updateUser(user.getUserId(), user.getFullName(), user.getEmail(), user.getPhone());
    }

    public User updateUser(String userId, String fullName, String email, String phone) {
        return coordinator.write(() -> {
            if (isBlank(userId)) throw new BusinessRuleViolationException("User ID must not be blank");
            User existing = requireUser(userId);
            validateProfile(fullName, email, phone);
            String normalizedEmail = normalizeEmail(email);
            userRepository.findByEmail(normalizedEmail).ifPresent(match -> {
                if (!match.getUserId().equals(existing.getUserId())) {
                    throw new BusinessRuleViolationException("User email already exists");
                }
            });
            existing.updateProfile(fullName.trim(), normalizedEmail, phone.trim());
            if (!userRepository.update(existing)) throw new ResourceNotFoundException("User not found: " + userId);
            return existing;
        });
    }

    public void deleteUser(String userId) {
        coordinator.write(() -> {
            mutationLock.acquire(MutationLock.platformAdministrators());
            User user = requireUser(userId);
            if (isLastActivePlatformAdministrator(user)) {
                throw new BusinessRuleViolationException("The last active platform administrator cannot be deleted");
            }
            boolean hasVehicles = !user.getVehicles().isEmpty()
                    || vehicleRepository != null && !vehicleRepository.findByUserId(userId).isEmpty();
            boolean hasBookings = !user.getBookings().isEmpty()
                    || bookingRepository != null && bookingRepository.existsByUserId(userId);
            if (hasVehicles || hasBookings) {
                throw new BusinessRuleViolationException(
                        "User cannot be deleted while vehicles or bookings still reference it");
            }
            if (notificationRepository != null) notificationRepository.deleteByUserId(userId);
            user.clearNotifications();
            TenantMembership membership = tenantMembershipRepository == null
                    ? null : tenantMembershipRepository.findById(userId).orElse(null);
            if (membership != null && !tenantMembershipRepository.deleteById(userId)) {
                throw new ResourceNotFoundException("Tenant membership not found");
            }
            if (!userRepository.deleteById(userId)) {
                ResourceNotFoundException failure = new ResourceNotFoundException("User not found: " + userId);
                coordinator.compensate(failure, () -> {
                    if (membership != null) tenantMembershipRepository.insert(membership);
                });
                throw failure;
            }
        });
    }

    public User assignRole(String userId, RoleName roleName) {
        return coordinator.write(() -> {
            mutationLock.acquire(MutationLock.platformAdministrators());
            User user = requireUser(userId);
            RoleName current = RoleCatalog.name(user.getRole());
            if (isOperational(current) || isOperational(roleName)) {
                throw new BusinessRuleViolationException(
                        "Operational role changes require the tenant membership operation");
            }
            if (isLastActivePlatformAdministrator(user) && roleName != RoleName.PLATFORM_ADMIN) {
                throw new BusinessRuleViolationException("The last active platform administrator cannot be demoted");
            }
            user.setRole(RoleCatalog.role(roleName));
            if (!userRepository.update(user)) throw new ResourceNotFoundException("User not found: " + userId);
            return user;
        });
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private boolean isLastActivePlatformAdministrator(User user) {
        return isActivePlatformAdministrator(user) && activePlatformAdministratorCount() <= 1;
    }

    private long activePlatformAdministratorCount() {
        return userRepository.findAll().stream().filter(this::isActivePlatformAdministrator).count();
    }

    private boolean isActivePlatformAdministrator(User user) {
        return user != null && user.getAccountStatus() == AccountStatus.ACTIVE && user.getRole() != null
                && RoleName.PLATFORM_ADMIN.name().equals(user.getRole().getRoleName());
    }

    private boolean isOperational(RoleName role) {
        return role == RoleName.STAFF || role == RoleName.BUSINESS_OWNER;
    }

    private void validateNewUser(CreateUserCommand command) {
        if (command == null) throw new BusinessRuleViolationException("Registration details are required");
        if (isBlank(command.userId())) throw new BusinessRuleViolationException("User ID must not be blank");
        validateProfile(command.fullName(), command.email(), command.phone());
    }

    private void validateProfile(String fullName, String email, String phone) {
        if (isBlank(fullName)) throw new BusinessRuleViolationException("Full name must not be blank");
        if (isBlank(email)) throw new BusinessRuleViolationException("Email must not be blank");
        if (!email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BusinessRuleViolationException("Email must be valid");
        }
        if (isBlank(phone)) throw new BusinessRuleViolationException("Phone must not be blank");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
