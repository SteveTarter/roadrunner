package com.tarterware.roadrunner.adapters.redis;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.models.mapbox.Isochrone;
import com.tarterware.roadrunner.ports.IsochroneCache;

@Component
public class RedisIsochroneCache implements IsochroneCache
{
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisIsochroneCache(RedisTemplate<String, Object> redisTemplate)
    {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<Isochrone> get(String cacheKey)
    {
        Object value = redisTemplate.opsForValue().get(cacheKey);
        return (value instanceof Isochrone isochrone)
                ? Optional.of(isochrone)
                : Optional.empty();
    }

    @Override
    public void put(String cacheKey, Isochrone isochrone, Duration ttl)
    {
        if (isochrone == null)
        {
            return;
        }
        redisTemplate.opsForValue().set(cacheKey, isochrone, ttl.toHours(), TimeUnit.HOURS);
    }

}
