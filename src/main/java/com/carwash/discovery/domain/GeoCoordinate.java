package com.carwash.discovery.domain;

/**
 * Immutable WGS84-style latitude/longitude pair used by distance adapters.
 * Values are expressed in decimal degrees.
 */
public record GeoCoordinate(double latitude, double longitude) {

    public GeoCoordinate {
        if (!Double.isFinite(latitude) || latitude < -90.0d || latitude > 90.0d) {
            throw new IllegalArgumentException("Latitude must be finite and between -90 and 90");
        }
        if (!Double.isFinite(longitude) || longitude < -180.0d || longitude > 180.0d) {
            throw new IllegalArgumentException("Longitude must be finite and between -180 and 180");
        }
    }
}
