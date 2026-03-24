package com.tarterware.roadrunner.adapters.redis;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.ports.DirectionsCache;

@Component
public class RedisDirectionsCache implements DirectionsCache
{

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisDirectionsCache(RedisTemplate<String, Object> redisTemplate)
    {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<Directions> get(String cacheKey)
    {
        Object value = redisTemplate.opsForValue().get(cacheKey);
        return (value instanceof Directions directions)
                ? Optional.of(directions)
                : Optional.empty();
    }

    @Override
    public void put(String cacheKey, Directions directions, Duration ttl)
    {
        if (directions == null)
        {
            return;
        }
        redisTemplate.opsForValue().set(cacheKey, directions, ttl.toHours(), TimeUnit.HOURS);
    }
}