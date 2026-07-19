package com.tarterware.roadrunner.ports;

import java.util.Optional;

import com.tarterware.roadrunner.models.mapbox.Directions;

public interface DirectionsCache
{
    Optional<Directions> get(String cacheKey);

    void put(String cacheKey, Directions directions);

    public void reset();
}