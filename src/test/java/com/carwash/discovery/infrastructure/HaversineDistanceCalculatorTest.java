package com.carwash.discovery.infrastructure;

import com.carwash.discovery.domain.GeoCoordinate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaversineDistanceCalculatorTest {

    private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();

    @Test
    void identicalCoordinatesProduceZeroDeterministically() {
        GeoCoordinate capeTown = coordinate(-33.9249, 18.4241);

        assertEquals(0.0d, calculator.distanceKm(capeTown, capeTown));
        assertEquals(
                Double.doubleToLongBits(calculator.distanceKm(capeTown, capeTown)),
                Double.doubleToLongBits(calculator.distanceKm(capeTown, capeTown)));
    }

    @Test
    void capeTownToJohannesburgMatchesKnownGreatCircleDistance() {
        double distance = calculator.distanceKm(
                coordinate(-33.9249, 18.4241),
                coordinate(-26.2041, 28.0473));

        assertEquals(1261.0d, distance, 2.0d,
                "Haversine distance should be approximately 1,261 km");
    }

    @Test
    void distanceIsSymmetricAcrossDirectionsAndInputOrder() {
        GeoCoordinate northWest = coordinate(-25.0, 18.0);
        GeoCoordinate southEast = coordinate(-34.0, 31.0);
        double forward = calculator.distanceKm(northWest, southEast);

        assertEquals(forward, calculator.distanceKm(southEast, northWest), 1.0e-9);
        assertEquals(
                calculator.distanceKm(coordinate(-25.0, 18.0), coordinate(-34.0, 18.0)),
                calculator.distanceKm(coordinate(-34.0, 18.0), coordinate(-25.0, 18.0)),
                1.0e-9);
        assertEquals(
                calculator.distanceKm(coordinate(-30.0, 18.0), coordinate(-30.0, 31.0)),
                calculator.distanceKm(coordinate(-30.0, 31.0), coordinate(-30.0, 18.0)),
                1.0e-9);
    }

    @Test
    void handlesAntimeridianPolesAndNearlyAntipodalCoordinates() {
        double antimeridian = calculator.distanceKm(coordinate(0.0, 179.9), coordinate(0.0, -179.9));
        double nearPole = calculator.distanceKm(coordinate(89.999, 0.0), coordinate(89.999, 179.999));
        double nearlyAntipodal = calculator.distanceKm(coordinate(0.000001, 0.0), coordinate(-0.000001, 179.999999));

        assertEquals(22.24d, antimeridian, 0.05d);
        assertTrue(Double.isFinite(nearPole) && nearPole > 0.0d);
        assertTrue(Double.isFinite(nearlyAntipodal));
        assertTrue(nearlyAntipodal <= Math.PI * HaversineDistanceCalculator.EARTH_RADIUS_KM);
    }

    @Test
    void acceptsCoordinateBoundariesAndTreatsEquivalentLongitudesAsIdentical() {
        assertTrue(Double.isFinite(calculator.distanceKm(coordinate(-90.0, -180.0), coordinate(90.0, 180.0))));
        assertEquals(0.0d, calculator.distanceKm(coordinate(0.0, -180.0), coordinate(0.0, 180.0)), 1.0e-9);
    }

    @Test
    void coordinateRejectsNonFiniteAndOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> coordinate(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> coordinate(Double.POSITIVE_INFINITY, 0.0));
        assertThrows(IllegalArgumentException.class, () -> coordinate(91.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> coordinate(-91.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> coordinate(0.0, 181.0));
        assertThrows(IllegalArgumentException.class, () -> coordinate(0.0, -181.0));
    }

    @Test
    void calculatorRejectsMissingCoordinatePairs() {
        assertThrows(NullPointerException.class, () -> calculator.distanceKm(null, coordinate(0.0, 0.0)));
        assertThrows(NullPointerException.class, () -> calculator.distanceKm(coordinate(0.0, 0.0), null));
    }

    private GeoCoordinate coordinate(double latitude, double longitude) {
        return new GeoCoordinate(latitude, longitude);
    }
}
