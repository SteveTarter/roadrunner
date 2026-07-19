package com.tarterware.roadrunner.adapters.redis;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.models.mapbox.Isochrone;
import com.tarterware.roadrunner.ports.IsochroneCache;
import com.tarterware.roadrunner.services.KafkaTopicMetadataService;

import jakarta.annotation.PostConstruct;

@Component
public class RedisIsochroneCache implements IsochroneCache
{
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTopicMetadataService metadataService;
    private Duration ttl;

    @Value("${com.tarterware.roadrunner.kafka.topic.vehicle-position}")
    private String topicName;

    public static final String KEY_PREFIX = "Isochrone:";

    private static final Logger logger = LoggerFactory.getLogger(RedisIsochroneCache.class);

    public RedisIsochroneCache(
            RedisTemplate<String, Object> redisTemplate,
            KafkaTopicMetadataService metadataService)
    {
        this.redisTemplate = redisTemplate;
        this.metadataService = metadataService;
    }

    @PostConstruct
    public void init()
    {
        this.ttl = metadataService.getTopicRetention(topicName);
    }

    @Override
    public Optional<Isochrone> get(String key)
    {
        String cacheKey = getCacheKey(key);

        Object value = redisTemplate.opsForValue().get(cacheKey);

        if (value instanceof Isochrone)
        {
            // Update the ttl to this latest access time.
            redisTemplate.expire(cacheKey, ttl);

            Isochrone isochrone = (Isochrone) value;
            return Optional.of(isochrone);
        }

        return Optional.empty();
    }

    @Override
    public void put(String key, Isochrone isochrone)
    {
        String cacheKey = getCacheKey(key);

        if (isochrone == null)
        {
            return;
        }
        redisTemplate.opsForValue().set(cacheKey, isochrone, ttl);
    }

    @Override
    public void reset()
    {
        // Note: In production, scan is better than keys,
        // but for a reset() method, deleting the pattern is standard.
        var keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty())
        {
            logger.info("Resetting {} {}* keys)", keys.size(), KEY_PREFIX);
            redisTemplate.delete(keys);
        }
    }

    private String getCacheKey(String key)
    {
        return KEY_PREFIX + key;
    }

}
