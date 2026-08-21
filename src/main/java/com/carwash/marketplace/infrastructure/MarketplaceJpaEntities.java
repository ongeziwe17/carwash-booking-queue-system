package com.carwash.marketplace.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "businesses")
class BusinessJpaEntity {
    @Id @Column(name="business_id", length=64) String id;
    @Column(name="business_name", nullable=false, length=160) String name;
    @Column(name="contact_email", nullable=false, length=320) String email;
    @Column(name="contact_phone", nullable=false, length=32) String phone;
    @Column(name="registration_number", length=64) String registrationNumber;
    @Column(name="business_status", nullable=false, length=16) String status;
    @Column(name="registered_at", nullable=false, columnDefinition="timestamp(6)") LocalDateTime registeredAt;
    @Column(name="registered_at_nano_remainder", nullable=false) short registeredAtNano;
    @Column(name="updated_at", nullable=false, columnDefinition="timestamp(6)") LocalDateTime updatedAt;
    @Column(name="updated_at_nano_remainder", nullable=false) short updatedAtNano;
    @Version @Column(nullable=false) Long version;
    protected BusinessJpaEntity() { }
}

@Entity @Table(name = "branches")
class BranchJpaEntity {
    @Id @Column(name="branch_id", length=64) String id;
    @Column(name="business_id", nullable=false, length=64) String businessId;
    @Column(name="branch_name", nullable=false, length=160) String name;
    @Column(name="address_line1", nullable=false, length=255) String address1;
    @Column(name="address_line2", length=255) String address2;
    @Column(nullable=false, length=120) String city;
    @Column(nullable=false, length=120) String province;
    @Column(name="postal_code", nullable=false, length=32) String postalCode;
    @Column(name="country_code", nullable=false, length=2) String countryCode;
    @Column(nullable=false, precision=11, scale=8) BigDecimal latitude;
    @Column(nullable=false, precision=11, scale=8) BigDecimal longitude;
    @Column(nullable=false, length=64) String timezone;
    @Column(name="branch_status", nullable=false, length=16) String status;
    @Column(name="public_discovery_enabled", nullable=false) boolean publicDiscovery;
    @Column(name="created_at", nullable=false, columnDefinition="timestamp(6)") LocalDateTime createdAt;
    @Column(name="created_at_nano_remainder", nullable=false) short createdAtNano;
    @Column(name="updated_at", nullable=false, columnDefinition="timestamp(6)") LocalDateTime updatedAt;
    @Column(name="updated_at_nano_remainder", nullable=false) short updatedAtNano;
    @Version @Column(nullable=false) Long version;
    protected BranchJpaEntity() { }
}

@Entity @Table(name = "branch_operating_schedules")
class ScheduleJpaEntity {
    @Id @Column(name="branch_id", length=64) String branchId;
    @Column(name="created_at", nullable=false, columnDefinition="timestamp(6)") LocalDateTime createdAt;
    @Column(name="created_at_nano_remainder", nullable=false) short createdAtNano;
    @Column(name="updated_at", nullable=false, columnDefinition="timestamp(6)") LocalDateTime updatedAt;
    @Column(name="updated_at_nano_remainder", nullable=false) short updatedAtNano;
    @Version @Column(nullable=false) Long version;
    protected ScheduleJpaEntity() { }
}

@Entity @Table(name = "weekly_operating_intervals") @IdClass(WeeklyIntervalId.class)
class WeeklyIntervalJpaEntity {
    @Id @Column(name="branch_id", length=64) String branchId;
    @Id @Column(name="interval_order") int order;
    @Column(name="day_of_week", nullable=false) short day;
    @Column(name="opens_nano_of_day", nullable=false) long opensNano;
    @Column(name="closes_nano_of_day", nullable=false) long closesNano;
    protected WeeklyIntervalJpaEntity() { }
}

@Entity @Table(name = "temporary_branch_closures")
class ClosureJpaEntity {
    @Id @Column(name="closure_id", length=64) String id;
    @Column(name="branch_id", nullable=false, length=64) String branchId;
    @Column(name="start_epoch_second", nullable=false) long startSecond;
    @Column(name="start_nano", nullable=false) int startNano;
    @Column(name="end_epoch_second", nullable=false) long endSecond;
    @Column(name="end_nano", nullable=false) int endNano;
    @Column(nullable=false, length=1000) String reason;
    @Column(name="closure_status", nullable=false, length=16) String status;
    @Column(name="created_at", nullable=false, columnDefinition="timestamp(6)") LocalDateTime createdAt;
    @Column(name="created_at_nano_remainder", nullable=false) short createdAtNano;
    @Column(name="updated_at", nullable=false, columnDefinition="timestamp(6)") LocalDateTime updatedAt;
    @Column(name="updated_at_nano_remainder", nullable=false) short updatedAtNano;
    @Version @Column(nullable=false) Long version;
    protected ClosureJpaEntity() { }
}
