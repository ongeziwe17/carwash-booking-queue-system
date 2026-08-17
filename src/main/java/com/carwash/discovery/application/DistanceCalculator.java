package com.carwash.discovery.application;

import com.carwash.discovery.domain.GeoCoordinate;

/** Replaceable application port for straight-line distance calculations. */
public interface DistanceCalculator {

    double distanceKm(GeoCoordinate origin, GeoCoordinate destination);
}
