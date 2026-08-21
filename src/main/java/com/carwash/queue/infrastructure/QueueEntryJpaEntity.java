package com.carwash.queue.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

@Entity @Table(name="queue_entries")
class QueueEntryJpaEntity {
    @Id @Column(name="queue_entry_id",length=64) String id;
    @Column(name="booking_id",nullable=false,length=64) String bookingId;
    @Column(name="user_id",nullable=false,length=64) String userId;
    @Column(name="branch_id",nullable=false,length=64) String branchId;
    @Column(name="offering_id",nullable=false,length=64) String offeringId;
    @Column(name="service_id",nullable=false,length=64) String serviceId;
    @Column(nullable=false) int position;
    @Column(name="active_position") Integer activePosition;
    @Column(name="queue_status",nullable=false,length=24) String status;
    @Column(name="joined_at",nullable=false,columnDefinition="timestamp(6)") LocalDateTime joinedAt;
    @Column(name="joined_at_nano_remainder",nullable=false) short joinedAtNano;
    @Column(name="called_at",columnDefinition="timestamp(6)") LocalDateTime calledAt;
    @Column(name="called_at_nano_remainder",nullable=false) short calledAtNano;
    @Column(name="started_at",columnDefinition="timestamp(6)") LocalDateTime startedAt;
    @Column(name="started_at_nano_remainder",nullable=false) short startedAtNano;
    @Column(name="completed_at",columnDefinition="timestamp(6)") LocalDateTime completedAt;
    @Column(name="completed_at_nano_remainder",nullable=false) short completedAtNano;
    @Column(name="estimated_wait_min",nullable=false) int waitMinutes;
    @Version @Column(nullable=false) Long version;
    protected QueueEntryJpaEntity() { }
}
