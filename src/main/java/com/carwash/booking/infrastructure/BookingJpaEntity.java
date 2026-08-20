package com.carwash.booking.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
class BookingJpaEntity {
    @Id @Column(name="booking_id", length=64) String id;
    @Column(name="user_id", nullable=false, length=64) String userId;
    @Column(name="vehicle_id", nullable=false, length=64) String vehicleId;
    @Column(name="branch_id", nullable=false, length=64) String branchId;
    @Column(name="offering_id", nullable=false, length=64) String offeringId;
    @Column(name="service_id", nullable=false, length=64) String serviceId;
    @Column(name="scheduled_at", nullable=false, columnDefinition="timestamp(6)") LocalDateTime scheduledAt;
    @Column(name="scheduled_at_nano_remainder", nullable=false) short scheduledAtNano;
    @Column(name="booking_status", nullable=false, length=24) String status;
    @Column(name="created_at", nullable=false, columnDefinition="timestamp(6)") LocalDateTime createdAt;
    @Column(name="created_at_nano_remainder", nullable=false) short createdAtNano;
    @Column(name="special_request", length=2000) String specialRequest;
    @Column(name="queue_entry_id", length=64) String queueEntryId;
    @Version @Column(nullable=false) Long version;
    protected BookingJpaEntity() { }
}
