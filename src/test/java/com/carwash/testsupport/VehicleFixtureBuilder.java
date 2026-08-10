package com.carwash.testsupport;

import com.carwash.api.dto.CreateVehicleRequest;

public final class VehicleFixtureBuilder {

    private String userId;
    private String vehicleId;
    private String plateNumber;
    private String vehicleType = "SUV";
    private String brand = "Toyota";
    private String model = "Rav4";
    private String color = "Black";
    private String notes = "";

    private VehicleFixtureBuilder() {
    }

    public static VehicleFixtureBuilder valid(TestIdFactory ids, String userId) {
        VehicleFixtureBuilder builder = new VehicleFixtureBuilder();
        builder.userId = userId;
        builder.vehicleId = ids.vehicle();
        builder.plateNumber = ids.plate();
        return builder;
    }

    public VehicleFixtureBuilder userId(String value) { this.userId = value; return this; }
    public VehicleFixtureBuilder vehicleId(String value) { this.vehicleId = value; return this; }
    public VehicleFixtureBuilder plateNumber(String value) { this.plateNumber = value; return this; }
    public VehicleFixtureBuilder vehicleType(String value) { this.vehicleType = value; return this; }
    public VehicleFixtureBuilder brand(String value) { this.brand = value; return this; }
    public VehicleFixtureBuilder model(String value) { this.model = value; return this; }
    public VehicleFixtureBuilder color(String value) { this.color = value; return this; }
    public VehicleFixtureBuilder notes(String value) { this.notes = value; return this; }

    public CreateVehicleRequest build() {
        return new CreateVehicleRequest(userId, vehicleId, plateNumber, vehicleType, brand, model, color, notes);
    }
}
