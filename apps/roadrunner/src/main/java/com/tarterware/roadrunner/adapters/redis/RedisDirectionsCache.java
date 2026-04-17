package com.tarterware.roadrunner.adapters.redis;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.ports.DirectionsCache;
import com.tarterware.roadrunner.services.KafkaTopicMetadataService;

import jakarta.annotation.PostConstruct;

@Component
public class RedisDirectionsCache implements DirectionsCache
{
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTopicMetadataService metadataService;
    private Duration ttl;

    @Value("${com.tarterware.roadrunner.kafka.topic.vehicle-position}")
    private String topicName;

    public static final String KEY_PREFIX = "Directions:";

    private static final Logger logger = LoggerFactory.getLogger(RedisDirectionsCache.class);

    public RedisDirectionsCache(
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
    public Optional<Directions> get(String key)
    {
        String cacheKey = getCacheKey(key);

        Object value = redisTemplate.opsForValue().get(cacheKey);

        if (value instanceof Directions)
        {
            // Update the ttl to this latest access time.
            redisTemplate.expire(cacheKey, ttl);

            Directions directions = (Directions) value;
            return Optional.of(directions);
        }

        return Optional.empty();
    }

    @Override
    public void put(String key, Directions directions)
    {
        String cacheKey = getCacheKey(key);

        if (directions == null)
        {
            return;
        }

        redisTemplate.opsForValue().set(cacheKey, directions, ttl);
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