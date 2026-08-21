package com.carwash.identity.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "roles")
class RoleJpaEntity {
    @Id @Column(name = "role_id", length = 64) String id;
    @Column(name = "role_name", nullable = false, length = 32) String name;
    @Column(nullable = false, length = 255) String description;
    @Version @Column(nullable = false) Long version;

    protected RoleJpaEntity() { }
}

@Entity
@Table(name = "role_permissions")
@IdClass(RolePermissionId.class)
class RolePermissionJpaEntity {
    @Id @Column(name = "role_id", length = 64) String roleId;
    @Id @Column(nullable = false, length = 64) String permission;

    protected RolePermissionJpaEntity() { }
}

@Entity
@Table(name = "users")
class UserJpaEntity {
    @Id @Column(name = "user_id", length = 64) String id;
    @Column(name = "full_name", nullable = false, length = 160) String fullName;
    @Column(nullable = false, length = 320) String email;
    @Column(nullable = false, length = 40) String phone;
    @Column(name = "account_status", nullable = false, length = 24) String accountStatus;
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp(6)") LocalDateTime createdAt;
    @Column(name = "created_at_nano_remainder", nullable = false) short createdAtNano;
    @Column(name = "last_login_at", columnDefinition = "timestamp(6)") LocalDateTime lastLoginAt;
    @Column(name = "last_login_at_nano_remainder", nullable = false) short lastLoginAtNano;
    @Version @Column(nullable = false) Long version;

    protected UserJpaEntity() { }
}

@Entity
@Table(name = "user_credentials")
class UserCredentialJpaEntity {
    @Id @Column(name = "user_id", length = 64) String userId;
    @Column(name = "encoded_password", nullable = false, length = 255) String encodedPassword;
    @Version @Column(nullable = false) Long version;

    protected UserCredentialJpaEntity() { }
}

@Entity
@Table(name = "user_role_assignments")
class UserRoleAssignmentJpaEntity {
    @Id @Column(name = "user_id", length = 64) String userId;
    @Column(name = "role_id", nullable = false, length = 64) String roleId;
    @Version @Column(nullable = false) Long version;

    protected UserRoleAssignmentJpaEntity() { }
}
