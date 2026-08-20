package com.carwash.vehicle.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "vehicles")
class VehicleJpaEntity {
    @Id @Column(name = "vehicle_id", length = 64) String id;
    @Column(name = "user_id", nullable = false, length = 64) String userId;
    @Column(name = "plate_number", nullable = false, length = 32) String plateNumber;
    @Column(name = "vehicle_type", nullable = false, length = 64) String vehicleType;
    @Column(length = 80) String brand;
    @Column(length = 80) String model;
    @Column(length = 48) String color;
    @Column(length = 1000) String notes;
    @Version @Column(nullable = false) Long version;

    protected VehicleJpaEntity() { }
}
