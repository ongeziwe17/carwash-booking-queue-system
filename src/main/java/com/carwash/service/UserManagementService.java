package com.carwash.service;

import com.carwash.domain.User;
import com.carwash.repository.UserRepository;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Locale;

public class UserManagementService {

    private final UserRepository userRepository;

    public UserManagementService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(User user) {
        validateNewUser(user);
        normalizeProfile(user);
        userRepository.findByEmail(user.getEmail())
                .ifPresent(existing -> { throw new BusinessRuleViolationException("User email already exists"); });
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
        if (user == null) throw new BusinessRuleViolationException("User is required");
        return updateUser(user.getUserId(), user.getFullName(), user.getEmail(), user.getPhone());
    }

    public User updateUser(String userId, String fullName, String email, String phone) {
        if (isBlank(userId)) throw new BusinessRuleViolationException("User ID must not be blank");
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
        findById(userId);
        userRepository.delete(userId);
    }

    private void validateNewUser(User user) {
        if (user == null) throw new BusinessRuleViolationException("User is required");
        if (isBlank(user.getUserId())) throw new BusinessRuleViolationException("User ID must not be blank");
        validateProfile(user.getFullName(), user.getEmail(), user.getPhone());
        if (isBlank(user.getPasswordHash())) throw new BusinessRuleViolationException("Password must not be blank");
    }

    private void validateProfile(String fullName, String email, String phone) {
        if (isBlank(fullName)) throw new BusinessRuleViolationException("Full name must not be blank");
        if (isBlank(email)) throw new BusinessRuleViolationException("Email must not be blank");
        if (!email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BusinessRuleViolationException("Email must be valid");
        }
        if (isBlank(phone)) throw new BusinessRuleViolationException("Phone must not be blank");
    }

    private void normalizeProfile(User user) {
        user.setUserId(user.getUserId().trim());
        user.updateProfile(user.getFullName().trim(), normalizeEmail(user.getEmail()), user.getPhone().trim());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}