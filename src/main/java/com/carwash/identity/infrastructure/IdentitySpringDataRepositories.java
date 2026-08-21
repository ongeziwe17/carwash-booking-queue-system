package com.carwash.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

interface RoleSpringDataRepository extends JpaRepository<RoleJpaEntity, String> {
    List<RoleJpaEntity> findAllByOrderByIdAsc();
}

interface RolePermissionSpringDataRepository extends JpaRepository<RolePermissionJpaEntity, RolePermissionId> {
    List<RolePermissionJpaEntity> findByRoleIdOrderByPermissionAsc(String roleId);
    List<RolePermissionJpaEntity> findByRoleIdInOrderByRoleIdAscPermissionAsc(Collection<String> roleIds);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RolePermissionJpaEntity permission where permission.roleId=:roleId")
    int deleteByRoleId(String roleId);
}

interface UserSpringDataRepository extends JpaRepository<UserJpaEntity, String> {
    Optional<UserJpaEntity> findByEmailIgnoreCase(String email);
    List<UserJpaEntity> findAllByOrderByIdAsc();
}

interface UserCredentialSpringDataRepository extends JpaRepository<UserCredentialJpaEntity, String> { }

interface UserRoleAssignmentSpringDataRepository extends JpaRepository<UserRoleAssignmentJpaEntity, String> { }
