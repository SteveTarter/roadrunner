package com.tarterware.roadrunner.adapters.redis;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.models.mapbox.FeatureCollection;
import com.tarterware.roadrunner.ports.FeatureCollectionCache;

@Component
public class RedisFeatureCollectionCache implements FeatureCollectionCache
{

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisFeatureCollectionCache(RedisTemplate<String, Object> redisTemplate)
    {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<FeatureCollection> get(String cacheKey)
    {
        Object value = redisTemplate.opsForValue().get(cacheKey);
        return (value instanceof FeatureCollection featureCollection)
                ? Optional.of(featureCollection)
                : Optional.empty();
    }

    @Override
    public void put(String cacheKey, FeatureCollection featureCollection, Duration ttl)
    {
        if (featureCollection == null)
        {
            return;
        }
        redisTemplate.opsForValue().set(cacheKey, featureCollection, ttl.toHours(), TimeUnit.HOURS);
    }
}
