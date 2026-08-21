package com.carwash.notification.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

@Entity @Table(name="notifications")
class NotificationJpaEntity {
    @Id @Column(name="notification_id",length=128) String id;
    @Column(name="user_id",nullable=false,length=64) String userId;
    @Column(name="booking_id",length=64) String bookingId;
    @Column(name="branch_id",length=64) String branchId;
    @Column(name="offering_id",length=64) String offeringId;
    @Column(name="notification_type",nullable=false,length=64) String type;
    @Column(nullable=false,length=4000) String message;
    @Column(nullable=false,length=32) String channel;
    @Column(name="sent_at",columnDefinition="timestamp(6)") LocalDateTime sentAt;
    @Column(name="sent_at_nano_remainder",nullable=false) short sentAtNano;
    @Column(name="read_at",columnDefinition="timestamp(6)") LocalDateTime readAt;
    @Column(name="read_at_nano_remainder",nullable=false) short readAtNano;
    @Column(name="delivery_status",nullable=false,length=16) String status;
    @Version @Column(nullable=false) Long version;
    protected NotificationJpaEntity() { }
}
