package com.carwash.identity.infrastructure;

import com.carwash.identity.domain.AccountStatus;
import com.carwash.identity.domain.Role;
import com.carwash.identity.domain.User;
import com.carwash.identity.domain.UserRepository;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.infrastructure.PersistenceSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@Profile("postgres")
@Transactional
public class PostgresUserRepository implements UserRepository {
    private final UserSpringDataRepository users;
    private final UserCredentialSpringDataRepository credentials;
    private final UserRoleAssignmentSpringDataRepository assignments;
    private final PostgresRoleRepository roles;

    public PostgresUserRepository(UserSpringDataRepository users,
                                  UserCredentialSpringDataRepository credentials,
                                  UserRoleAssignmentSpringDataRepository assignments,
                                  PostgresRoleRepository roles) {
        this.users = users; this.credentials = credentials; this.assignments = assignments; this.roles = roles;
    }

    @Override public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return users.findByEmailIgnoreCase(email).map(this::domain);
    }

    @Override public boolean insert(User user) {
        if (users.existsById(user.getUserId())) return false;
        try {
            users.saveAndFlush(entity(user));
            credentials.saveAndFlush(credential(user));
            assignments.saveAndFlush(assignment(user));
            return true;
        } catch (DataIntegrityViolationException failure) {
            if (PersistenceSupport.constraint(failure, "uq_users_email_ci")) {
                throw new BusinessRuleViolationException("User email already exists");
            }
            throw new BusinessRuleViolationException("User conflicts with existing data");
        }
    }

    @Override public boolean update(User user) {
        Optional<UserJpaEntity> found = users.findById(user.getUserId());
        if (found.isEmpty()) return false;
        apply(user, found.get());
        try {
            users.saveAndFlush(found.get());
            UserCredentialJpaEntity credential = credentials.findById(user.getUserId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Credential missing for user " + user.getUserId()));
            credential.userId = user.getUserId(); credential.encodedPassword = user.getEncodedPassword();
            credentials.saveAndFlush(credential);
            UserRoleAssignmentJpaEntity assignment = assignments.findById(user.getUserId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Role assignment missing for user " + user.getUserId()));
            assignment.userId = user.getUserId(); assignment.roleId = user.getRole().getRoleId();
            assignments.saveAndFlush(assignment);
            return true;
        } catch (DataIntegrityViolationException failure) {
            if (PersistenceSupport.constraint(failure, "uq_users_email_ci")) {
                throw new BusinessRuleViolationException("User email already exists");
            }
            throw new BusinessRuleViolationException("User conflicts with existing data");
        }
    }

    @Override public Optional<User> findById(String id) { return users.findById(id).map(this::domain); }
    @Override public List<User> findAll() {
        List<UserJpaEntity> found = users.findAllByOrderByIdAsc();
        List<String> ids = found.stream().map(entity -> entity.id).toList();
        Map<String, UserCredentialJpaEntity> credentialByUser = credentials.findAllById(ids).stream()
                .collect(Collectors.toMap(entity -> entity.userId, Function.identity()));
        Map<String, UserRoleAssignmentJpaEntity> assignmentByUser = assignments.findAllById(ids).stream()
                .collect(Collectors.toMap(entity -> entity.userId, Function.identity()));
        Map<String, Role> roleById = roles.findByIds(assignmentByUser.values().stream()
                .map(entity -> entity.roleId).distinct().toList());
        return found.stream().map(entity -> domain(
                entity,
                requireAssociated(credentialByUser, entity.id, "Credential"),
                requireAssociated(assignmentByUser, entity.id, "Role assignment"),
                roleById
        )).toList();
    }
    @Override public boolean deleteById(String id) {
        Optional<UserJpaEntity> found = users.findById(id);
        if (found.isEmpty()) return false;
        users.delete(found.get()); users.flush(); return true;
    }
    @Override public boolean existsById(String id) { return users.existsById(id); }

    private User domain(UserJpaEntity entity) {
        UserCredentialJpaEntity credential = credentials.findById(entity.id)
                .orElseThrow(() -> new IllegalStateException("Credential missing for user " + entity.id));
        UserRoleAssignmentJpaEntity assignment = assignments.findById(entity.id)
                .orElseThrow(() -> new IllegalStateException("Role assignment missing for user " + entity.id));
        Role role = roles.findById(assignment.roleId)
                .orElseThrow(() -> new IllegalStateException("Role missing for user " + entity.id));
        return domain(entity, credential, assignment, Map.of(role.getRoleId(), role));
    }

    private User domain(UserJpaEntity entity,
                        UserCredentialJpaEntity credential,
                        UserRoleAssignmentJpaEntity assignment,
                        Map<String, Role> roleById) {
        Role role = Optional.ofNullable(roleById.get(assignment.roleId))
                .orElseThrow(() -> new IllegalStateException("Role missing for user " + entity.id));
        User user = User.withEncodedPassword(entity.id, entity.fullName, entity.email, entity.phone,
                credential.encodedPassword, role);
        user.setAccountStatus(AccountStatus.valueOf(entity.accountStatus));
        user.setCreatedAt(PersistenceSupport.domainTime(entity.createdAt, entity.createdAtNano));
        user.setLastLoginAt(PersistenceSupport.domainTime(entity.lastLoginAt, entity.lastLoginAtNano));
        return user;
    }

    private static <T> T requireAssociated(Map<String, T> values, String userId, String association) {
        return Optional.ofNullable(values.get(userId))
                .orElseThrow(() -> new IllegalStateException(association + " missing for user " + userId));
    }

    private static UserJpaEntity entity(User user) { UserJpaEntity entity = new UserJpaEntity(); apply(user, entity); return entity; }
    private static void apply(User user, UserJpaEntity entity) {
        entity.id = user.getUserId(); entity.fullName = user.getFullName(); entity.email = user.getEmail();
        entity.phone = user.getPhone(); entity.accountStatus = user.getAccountStatus().name();
        entity.createdAt = PersistenceSupport.databaseTime(user.getCreatedAt());
        entity.createdAtNano = PersistenceSupport.nanoRemainder(user.getCreatedAt());
        entity.lastLoginAt = PersistenceSupport.databaseTime(user.getLastLoginAt());
        entity.lastLoginAtNano = PersistenceSupport.nanoRemainder(user.getLastLoginAt());
    }
    private static UserCredentialJpaEntity credential(User user) {
        UserCredentialJpaEntity entity = new UserCredentialJpaEntity(); entity.userId = user.getUserId();
        entity.encodedPassword = user.getEncodedPassword(); return entity;
    }
    private static UserRoleAssignmentJpaEntity assignment(User user) {
        UserRoleAssignmentJpaEntity entity = new UserRoleAssignmentJpaEntity(); entity.userId = user.getUserId();
        entity.roleId = user.getRole().getRoleId(); return entity;
    }
}
