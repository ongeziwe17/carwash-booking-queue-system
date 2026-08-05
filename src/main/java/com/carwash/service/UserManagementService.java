package com.carwash.service;

import com.carwash.domain.User;
import com.carwash.enums.AccountStatus;
import com.carwash.repository.BookingRepository;
import com.carwash.repository.NotificationRepository;
import com.carwash.repository.UserRepository;
import com.carwash.repository.VehicleRepository;
import com.carwash.repository.inmemory.InMemoryDataCoordinator;
import com.carwash.security.RoleCatalog;
import com.carwash.security.RoleName;
import com.carwash.security.UserCredentialService;
import com.carwash.service.command.CreateUserCommand;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class UserManagementService {

    private final UserRepository userRepository;
    private final UserCredentialService credentialService;
    private final VehicleRepository vehicleRepository;
    private final BookingRepository bookingRepository;
    private final NotificationRepository notificationRepository;
    private final InMemoryDataCoordinator coordinator;


    public UserManagementService(
            UserRepository userRepository,
            UserCredentialService credentialService,
            VehicleRepository vehicleRepository,
            BookingRepository bookingRepository,
            NotificationRepository notificationRepository,
            InMemoryDataCoordinator coordinator
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "User repository is required");
        this.credentialService = Objects.requireNonNull(credentialService, "Credential service is required");
        this.vehicleRepository = vehicleRepository;
        this.bookingRepository = bookingRepository;
        this.notificationRepository = notificationRepository;
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
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
            if (!userRepository.deleteById(userId)) throw new ResourceNotFoundException("User not found: " + userId);
        });
    }

    public User assignRole(String userId, RoleName roleName) {
        return coordinator.write(() -> {
            User user = requireUser(userId);
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
