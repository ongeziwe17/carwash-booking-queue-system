package com.carwash.identity.application;

import com.carwash.identity.domain.RoleCatalog;
import com.carwash.identity.domain.RoleName;
import com.carwash.identity.domain.AccountStatus;
import com.carwash.identity.domain.TenantMembership;
import com.carwash.identity.domain.TenantMembershipRepository;
import com.carwash.identity.domain.User;
import com.carwash.identity.domain.UserRepository;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.application.MutationLock;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Platform-admin onboarding boundary for role and operational tenant membership changes. */
public final class TenantMembershipManagementService implements TenantMembershipQuery {

    private final UserRepository users;
    private final TenantMembershipRepository memberships;
    private final TenantBusinessQuery businesses;
    private final DataTransactionOperations transactions;
    private final MutationLock mutationLock;
    private final Clock clock;

    public TenantMembershipManagementService(
            UserRepository users,
            TenantMembershipRepository memberships,
            TenantBusinessQuery businesses,
            DataTransactionOperations transactions,
            MutationLock mutationLock,
            Clock clock
    ) {
        this.users = Objects.requireNonNull(users, "User repository is required");
        this.memberships = Objects.requireNonNull(memberships, "Tenant membership repository is required");
        this.businesses = Objects.requireNonNull(businesses, "Tenant business query is required");
        this.transactions = Objects.requireNonNull(transactions, "Data transactions are required");
        this.mutationLock = Objects.requireNonNull(mutationLock, "Mutation lock is required");
        this.clock = Objects.requireNonNull(clock, "Application clock is required");
    }

    public TenantMembership assignOrReplace(String userId, String businessId) {
        return transactions.write(() -> {
            mutationLock.acquire(MutationLock.platformAdministrators());
            User user = requireUser(userId);
            requireOperationalRole(user);
            String tenantId = normalizeId(businessId, "Business ID");
            if (!businesses.exists(tenantId)) {
                throw new ResourceNotFoundException("Business not found");
            }
            TenantMembership replacement = new TenantMembership(
                    user.getUserId(), tenantId, LocalDateTime.now(clock));
            Optional<TenantMembership> previous = memberships.findById(user.getUserId());
            boolean stored = previous.isPresent()
                    ? memberships.update(replacement)
                    : memberships.insert(replacement);
            if (!stored) {
                throw new BusinessRuleViolationException("Tenant membership could not be assigned");
            }
            return replacement;
        });
    }

    /** Removes the assignment and demotes the user atomically so no new invalid operational identity is created. */
    public User removeAndDemote(String userId) {
        return transactions.write(() -> {
            mutationLock.acquire(MutationLock.platformAdministrators());
            User user = requireUser(userId);
            TenantMembership previous = memberships.findById(user.getUserId()).orElse(null);
            if (previous != null && !memberships.deleteById(user.getUserId())) {
                throw new ResourceNotFoundException("Tenant membership not found");
            }
            var originalRole = user.getRole();
            try {
                user.setRole(RoleCatalog.role(RoleName.CUSTOMER));
                if (!users.update(user)) {
                    throw new ResourceNotFoundException("User not found");
                }
            } catch (RuntimeException failure) {
                transactions.compensate(failure, () -> {
                    user.setRole(originalRole);
                    if (previous != null) memberships.insert(previous);
                });
                throw failure;
            }
            return user;
        });
    }

    /** Assigns a role and its required membership in one transaction. */
    public User assignRole(String userId, RoleName roleName, String businessId) {
        return transactions.write(() -> {
            mutationLock.acquire(MutationLock.platformAdministrators());
            User user = requireUser(userId);
            RoleName target = Objects.requireNonNull(roleName, "Role name is required");
            if (isLastActivePlatformAdministrator(user) && target != RoleName.PLATFORM_ADMIN) {
                throw new BusinessRuleViolationException("The last active platform administrator cannot be demoted");
            }
            TenantMembership previous = memberships.findById(user.getUserId()).orElse(null);
            var originalRole = user.getRole();
            try {
                if (isOperational(target)) {
                    String tenantId = normalizeId(businessId, "Business ID");
                    if (!businesses.exists(tenantId)) {
                        throw new ResourceNotFoundException("Business not found");
                    }
                    TenantMembership assignment = new TenantMembership(
                            user.getUserId(), tenantId, LocalDateTime.now(clock));
                    boolean stored = previous == null
                            ? memberships.insert(assignment)
                            : memberships.update(assignment);
                    if (!stored) throw new BusinessRuleViolationException("Tenant membership could not be assigned");
                } else {
                    if (businessId != null && !businessId.isBlank()) {
                        throw new BusinessRuleViolationException(
                                "Customer and platform administrator roles cannot have a tenant membership");
                    }
                    if (previous != null && !memberships.deleteById(user.getUserId())) {
                        throw new ResourceNotFoundException("Tenant membership not found");
                    }
                }
                user.setRole(RoleCatalog.role(target));
                if (!users.update(user)) throw new ResourceNotFoundException("User not found");
                return user;
            } catch (RuntimeException failure) {
                transactions.compensate(failure, () -> {
                    user.setRole(originalRole);
                    users.update(user);
                    memberships.findById(user.getUserId()).ifPresent(ignored -> memberships.deleteById(user.getUserId()));
                    if (previous != null) memberships.insert(previous);
                });
                throw failure;
            }
        });
    }

    @Override
    public Optional<TenantMembership> findByUserId(String userId) {
        if (userId == null || userId.isBlank()) return Optional.empty();
        return transactions.read(() -> memberships.findById(userId.trim()));
    }

    @Override
    public List<TenantMembership> findByBusinessId(String businessId) {
        String tenantId = normalizeId(businessId, "Business ID");
        return transactions.read(() -> memberships.findByBusinessId(tenantId));
    }

    private User requireUser(String userId) {
        String normalized = normalizeId(userId, "User ID");
        return users.findById(normalized).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void requireOperationalRole(User user) {
        RoleName role = RoleCatalog.name(user.getRole());
        if (!isOperational(role)) {
            throw new BusinessRuleViolationException("Only staff and business owners can have a tenant membership");
        }
    }

    private static boolean isOperational(RoleName role) {
        return role == RoleName.STAFF || role == RoleName.BUSINESS_OWNER;
    }

    private boolean isLastActivePlatformAdministrator(User user) {
        return user.getAccountStatus() == AccountStatus.ACTIVE
                && RoleCatalog.name(user.getRole()) == RoleName.PLATFORM_ADMIN
                && users.findAll().stream()
                .filter(candidate -> candidate.getAccountStatus() == AccountStatus.ACTIVE)
                .filter(candidate -> RoleCatalog.name(candidate.getRole()) == RoleName.PLATFORM_ADMIN)
                .count() <= 1;
    }

    private String normalizeId(String value, String field) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new BusinessRuleViolationException(field + " is required");
        }
        if (normalized.length() > 64) {
            throw new BusinessRuleViolationException(field + " must not exceed 64 characters");
        }
        return normalized;
    }
}
