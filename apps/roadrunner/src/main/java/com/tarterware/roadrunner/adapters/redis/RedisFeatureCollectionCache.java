package com.tarterware.roadrunner.adapters.redis;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.models.mapbox.FeatureCollection;
import com.tarterware.roadrunner.ports.FeatureCollectionCache;
import com.tarterware.roadrunner.services.KafkaTopicMetadataService;

import jakarta.annotation.PostConstruct;

@Component
public class RedisFeatureCollectionCache implements FeatureCollectionCache
{
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTopicMetadataService metadataService;
    private Duration ttl;

    @Value("${com.tarterware.roadrunner.kafka.topic.vehicle-position}")
    private String topicName;

    public static final String KEY_PREFIX = "FeatureCollection:";

    private static final Logger logger = LoggerFactory.getLogger(RedisFeatureCollectionCache.class);

    public RedisFeatureCollectionCache(
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
    public Optional<FeatureCollection> get(String key)
    {
        String cacheKey = getCacheKey(key);

        Object value = redisTemplate.opsForValue().get(cacheKey);

        if (value instanceof FeatureCollection)
        {
            // Update the ttl to this latest access time.
            redisTemplate.expire(cacheKey, ttl);

            FeatureCollection featureCollection = (FeatureCollection) value;
            return Optional.of(featureCollection);
        }

        return Optional.empty();
    }

    @Override
    public void put(String key, FeatureCollection featureCollection)
    {
        String cacheKey = getCacheKey(key);

        if (featureCollection == null)
        {
            return;
        }

        redisTemplate.opsForValue().set(cacheKey, featureCollection, ttl);
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
