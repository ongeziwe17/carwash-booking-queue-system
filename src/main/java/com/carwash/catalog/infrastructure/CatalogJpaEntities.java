package com.carwash.catalog.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_definitions")
class ServiceJpaEntity {
    @Id @Column(name = "service_id", length = 64) String id;
    @Column(name = "service_name", nullable = false, length = 160) String name;
    @Column(length = 1000) String description;
    @Column(name = "legacy_price", nullable = false, precision = 12, scale = 2) BigDecimal price;
    @Column(name = "legacy_duration_min", nullable = false) int duration;
    @Column(nullable = false) boolean active;
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp(6)") LocalDateTime createdAt;
    @Column(name = "created_at_nano_remainder", nullable = false) short createdAtNano;
    @Version @Column(nullable = false) Long version;
    protected ServiceJpaEntity() { }
}

@Entity
@Table(name = "service_offerings")
class ServiceOfferingJpaEntity {
    @Id @Column(name = "offering_id", length = 64) String id;
    @Column(name = "branch_id", nullable = false, length = 64) String branchId;
    @Column(name = "service_id", nullable = false, length = 64) String serviceId;
    @Column(nullable = false, precision = 12, scale = 2) BigDecimal price;
    @Column(name = "estimated_duration_min", nullable = false) int duration;
    @Column(name = "concurrent_capacity", nullable = false) int capacity;
    @Column(name = "offering_status", nullable = false, length = 16) String status;
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp(6)") LocalDateTime createdAt;
    @Column(name = "created_at_nano_remainder", nullable = false) short createdAtNano;
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp(6)") LocalDateTime updatedAt;
    @Column(name = "updated_at_nano_remainder", nullable = false) short updatedAtNano;
    @Version @Column(nullable = false) Long version;
    protected ServiceOfferingJpaEntity() { }
}
