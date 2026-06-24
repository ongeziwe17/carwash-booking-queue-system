package com.carwash.domain;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Vehicle {
    private String vehicleId;
    private String plateNumber;
    private String vehicleType;
    private String brand;
    private String model;
    private String color;
    private String notes;
    private String userId;

    public Vehicle() {
    }

    public Vehicle(String vehicleId, String plateNumber, String vehicleType, String brand, String model, String color, String notes) {
        this.vehicleId = vehicleId;
        this.plateNumber = plateNumber;
        this.vehicleType = vehicleType;
        this.brand = brand;
        this.model = model;
        this.color = color;
        this.notes = notes;
    }

    public boolean validateOwnership(User user) {
        return user != null && user.getVehicles().contains(this);
    }

    public void updateVehicleDetails(String brand, String model, String color, String notes) {
        this.brand = brand;
        this.model = model;
        this.color = color;
        this.notes = notes;
    }

}
