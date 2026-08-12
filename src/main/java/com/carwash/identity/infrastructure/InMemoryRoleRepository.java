package com.carwash.identity.infrastructure;

import com.carwash.shared.infrastructure.InMemoryRepository;

import com.carwash.identity.domain.Role;
import com.carwash.identity.domain.RoleRepository;

public class InMemoryRoleRepository extends InMemoryRepository<Role, String> implements RoleRepository {
    @Override
    protected String getId(Role entity) {
        return entity.getRoleId();
    }
}
