package com.tarterware.roadrunner.ports;

import java.time.Duration;
import java.util.Optional;

import com.tarterware.roadrunner.models.mapbox.Directions;

public interface DirectionsCache
{
    Optional<Directions> get(String cacheKey);

    void put(String cacheKey, Directions directions, Duration ttl);
}