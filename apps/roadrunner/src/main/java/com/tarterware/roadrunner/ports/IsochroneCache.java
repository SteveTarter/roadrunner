package com.tarterware.roadrunner.ports;

import java.time.Duration;
import java.util.Optional;

import com.tarterware.roadrunner.models.mapbox.Isochrone;

public interface IsochroneCache
{
    Optional<Isochrone> get(String cacheKey);

    void put(String cacheKey, Isochrone directions, Duration ttl);
}