package com.tarterware.roadrunner.ports;

import java.time.Duration;
import java.util.Optional;

import com.tarterware.roadrunner.models.mapbox.FeatureCollection;

public interface FeatureCollectionCache
{
    Optional<FeatureCollection> get(String cacheKey);

    void put(String cacheKey, FeatureCollection featureCollection, Duration ttl);
}
