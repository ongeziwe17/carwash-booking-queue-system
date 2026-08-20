package com.carwash.identity.infrastructure;

import com.carwash.identity.domain.Role;
import com.carwash.identity.domain.RoleRepository;
import com.carwash.shared.exception.BusinessRuleViolationException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("postgres")
@Transactional
public class PostgresRoleRepository implements RoleRepository {
    private final RoleSpringDataRepository roles;
    private final RolePermissionSpringDataRepository permissions;

    public PostgresRoleRepository(RoleSpringDataRepository roles, RolePermissionSpringDataRepository permissions) {
        this.roles = roles;
        this.permissions = permissions;
    }

    @Override public boolean insert(Role role) {
        if (roles.existsById(role.getRoleId())) return false;
        RoleJpaEntity entity = new RoleJpaEntity();
        apply(role, entity);
        try {
            roles.saveAndFlush(entity);
            savePermissions(role);
            return true;
        } catch (DataIntegrityViolationException failure) {
            throw new BusinessRuleViolationException("Role conflicts with existing data");
        }
    }

    @Override public boolean update(Role role) {
        Optional<RoleJpaEntity> found = roles.findById(role.getRoleId());
        if (found.isEmpty()) return false;
        apply(role, found.get());
        roles.saveAndFlush(found.get());
        permissions.deleteByRoleId(role.getRoleId());
        permissions.flush();
        savePermissions(role);
        return true;
    }

    @Override public Optional<Role> findById(String id) { return roles.findById(id).map(this::domain); }
    @Override public List<Role> findAll() { return findByIds(roles.findAllByOrderByIdAsc().stream()
            .map(entity -> entity.id).toList()).values().stream().toList(); }
    @Override public boolean deleteById(String id) {
        Optional<RoleJpaEntity> found = roles.findById(id);
        if (found.isEmpty()) return false;
        roles.delete(found.get()); roles.flush(); return true;
    }
    @Override public boolean existsById(String id) { return roles.existsById(id); }

    private Role domain(RoleJpaEntity entity) {
        return domain(entity, permissions.findByRoleIdOrderByPermissionAsc(entity.id));
    }

    Map<String, Role> findByIds(Collection<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return Map.of();
        List<RoleJpaEntity> entities = roles.findAllById(roleIds).stream()
                .sorted(Comparator.comparing(entity -> entity.id)).toList();
        Map<String, List<RolePermissionJpaEntity>> permissionsByRole = permissions
                .findByRoleIdInOrderByRoleIdAscPermissionAsc(roleIds).stream()
                .collect(Collectors.groupingBy(entity -> entity.roleId));
        return entities.stream().map(entity -> domain(
                        entity, permissionsByRole.getOrDefault(entity.id, List.of())))
                .collect(Collectors.toMap(Role::getRoleId, Function.identity(), (left, right) -> left,
                        java.util.LinkedHashMap::new));
    }

    private Role domain(RoleJpaEntity entity, List<RolePermissionJpaEntity> associatedPermissions) {
        HashSet<String> values = new HashSet<>();
        associatedPermissions.forEach(item -> values.add(item.permission));
        return new Role(entity.id, entity.name, entity.description, values);
    }

    private static void apply(Role source, RoleJpaEntity target) {
        target.id = source.getRoleId(); target.name = source.getRoleName(); target.description = source.getDescription();
    }

    private void savePermissions(Role role) {
        permissions.saveAll(role.getPermissions().stream().sorted().map(value -> {
            RolePermissionJpaEntity entity = new RolePermissionJpaEntity();
            entity.roleId = role.getRoleId(); entity.permission = value; return entity;
        }).toList());
        permissions.flush();
    }
}
