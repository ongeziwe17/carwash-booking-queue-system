package com.carwash.service;

import com.carwash.domain.User;
import com.carwash.enums.AccountStatus;
import com.carwash.repository.UserRepository;
import com.carwash.security.RoleCatalog;
import com.carwash.security.RoleName;
import com.carwash.security.UserCredentialService;
import com.carwash.service.command.CreateUserCommand;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Locale;

public class UserManagementService {

    private final UserRepository userRepository;
    private final UserCredentialService credentialService;

    /**
     * Serializes operations that can remove an active platform administrator.
     *
     * <p>This makes the count-and-mutate sequence atomic for the current single-JVM,
     * in-memory implementation. When persistence is introduced, this invariant must
     * move to a database transaction with suitable locking or another repository-level
     * atomic operation so it also holds across application instances.</p>
     */
    private final Object platformAdministratorMutationMonitor = new Object();

    public UserManagementService(UserRepository userRepository, UserCredentialService credentialService) {
        this.userRepository = userRepository;
        this.credentialService = credentialService;
    }

    public User createUser(CreateUserCommand command) {
        validateNewUser(command);
        String userId = command.userId().trim();
        String fullName = command.fullName().trim();
        String email = normalizeEmail(command.email());
        String phone = command.phone().trim();
        credentialService.validatePolicy(command.rawPassword());
        userRepository.findByEmail(email)
                .ifPresent(existing -> {
                    throw new BusinessRuleViolationException("User email already exists");
                });
        String encodedPassword = credentialService.encode(command.rawPassword());
        User user = User.withEncodedPassword(userId, fullName, email, phone, encodedPassword,
                RoleCatalog.role(RoleName.CUSTOMER));
        user.registerAccount();
        userRepository.save(user);
        return user;
    }

    public User findById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User updateUser(User user) {
        if (user == null) {
            throw new BusinessRuleViolationException("User is required");
        }
        return updateUser(user.getUserId(), user.getFullName(), user.getEmail(), user.getPhone());
    }

    public User updateUser(String userId, String fullName, String email, String phone) {
        if (isBlank(userId)) {
            throw new BusinessRuleViolationException("User ID must not be blank");
        }
        User existing = findById(userId);
        validateProfile(fullName, email, phone);
        String normalizedEmail = normalizeEmail(email);
        userRepository.findByEmail(normalizedEmail).ifPresent(match -> {
            if (!match.getUserId().equals(existing.getUserId())) {
                throw new BusinessRuleViolationException("User email already exists");
            }
        });
        existing.updateProfile(fullName.trim(), normalizedEmail, phone.trim());
        userRepository.save(existing);
        return existing;
    }

    public void deleteUser(String userId) {
        synchronized (platformAdministratorMutationMonitor) {
            User user = findById(userId);
            if (isLastActivePlatformAdministrator(user)) {
                throw new BusinessRuleViolationException("The last active platform administrator cannot be deleted");
            }
            userRepository.delete(userId);
        }
    }

    public User assignRole(String userId, RoleName roleName) {
        synchronized (platformAdministratorMutationMonitor) {
            User user = findById(userId);
            if (isLastActivePlatformAdministrator(user) && roleName != RoleName.PLATFORM_ADMIN) {
                throw new BusinessRuleViolationException("The last active platform administrator cannot be demoted");
            }
            user.setRole(RoleCatalog.role(roleName));
            userRepository.save(user);
            return user;
        }
    }

    private boolean isLastActivePlatformAdministrator(User user) {
        return isActivePlatformAdministrator(user) && activePlatformAdministratorCount() <= 1;
    }

    private long activePlatformAdministratorCount() {
        return userRepository.findAll().stream()
                .filter(this::isActivePlatformAdministrator)
                .count();
    }

    private boolean isActivePlatformAdministrator(User user) {
        return user != null
                && user.getAccountStatus() == AccountStatus.ACTIVE
                && user.getRole() != null
                && RoleName.PLATFORM_ADMIN.name().equals(user.getRole().getRoleName());
    }

    private void validateNewUser(CreateUserCommand command) {
        if (command == null) {
            throw new BusinessRuleViolationException("Registration details are required");
        }
        if (isBlank(command.userId())) {
            throw new BusinessRuleViolationException("User ID must not be blank");
        }
        validateProfile(command.fullName(), command.email(), command.phone());
    }

    private void validateProfile(String fullName, String email, String phone) {
        if (isBlank(fullName)) {
            throw new BusinessRuleViolationException("Full name must not be blank");
        }
        if (isBlank(email)) {
            throw new BusinessRuleViolationException("Email must not be blank");
        }
        if (!email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BusinessRuleViolationException("Email must be valid");
        }
        if (isBlank(phone)) {
            throw new BusinessRuleViolationException("Phone must not be blank");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
