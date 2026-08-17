package com.carwash.discovery.infrastructure;

import com.carwash.discovery.application.DistanceCalculator;
import com.carwash.discovery.domain.GeoCoordinate;

import java.util.Objects;

/** Haversine great-circle distance using the IUGG mean Earth radius. */
public final class HaversineDistanceCalculator implements DistanceCalculator {

    public static final double EARTH_RADIUS_KM = 6371.0088d;

    @Override
    public double distanceKm(GeoCoordinate origin, GeoCoordinate destination) {
        Objects.requireNonNull(origin, "Origin coordinate is required");
        Objects.requireNonNull(destination, "Destination coordinate is required");

        double originLatitude = Math.toRadians(origin.latitude());
        double destinationLatitude = Math.toRadians(destination.latitude());
        double latitudeDelta = destinationLatitude - originLatitude;
        double longitudeDelta = normalizedLongitudeDelta(
                Math.toRadians(destination.longitude() - origin.longitude()));

        double sinLatitude = Math.sin(latitudeDelta / 2.0d);
        double sinLongitude = Math.sin(longitudeDelta / 2.0d);
        double haversine = sinLatitude * sinLatitude
                + Math.cos(originLatitude) * Math.cos(destinationLatitude) * sinLongitude * sinLongitude;
        double clamped = Math.max(0.0d, Math.min(1.0d, haversine));
        double centralAngle = 2.0d * Math.atan2(Math.sqrt(clamped), Math.sqrt(1.0d - clamped));
        return EARTH_RADIUS_KM * centralAngle;
    }

    private double normalizedLongitudeDelta(double radians) {
        double fullTurn = 2.0d * Math.PI;
        double normalized = (radians + Math.PI) % fullTurn;
        if (normalized < 0.0d) {
            normalized += fullTurn;
        }
        return normalized - Math.PI;
    }
}
