package com.tarterware.roadrunner.adapters.redis;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.ports.TripPlanRepository;
import com.tarterware.roadrunner.services.KafkaTopicMetadataService;

import jakarta.annotation.PostConstruct;

@Component
public class RedisTripPlanRepository implements TripPlanRepository
{
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTopicMetadataService metadataService;
    private Duration ttl;

    @Value("${com.tarterware.roadrunner.kafka.topic.vehicle-position}")
    private String topicName;

    public static final String KEY_PREFIX = "TripPlan:";

    private static final Logger logger = LoggerFactory.getLogger(RedisTripPlanRepository.class);

    public RedisTripPlanRepository(
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
    public TripPlan getTripPlan(UUID vehicleId)
    {
        String cacheKey = getCacheKey(vehicleId);

        Object value = redisTemplate.opsForValue().get(cacheKey);

        if (value instanceof TripPlan)
        {
            // Update the ttl to this latest access time.
            redisTemplate.expire(cacheKey, ttl);

            TripPlan tripPlan = (TripPlan) value;
            return tripPlan;
        }

        return null;
    }

    @Override
    public void saveTripPlan(UUID vehicleId, TripPlan tripPlan)
    {
        if (tripPlan == null)
        {
            return;

        }

        redisTemplate.opsForValue().set(getCacheKey(vehicleId), tripPlan, ttl);
    }

    @Override
    public void deleteTripPlan(UUID vehicleId)
    {
        redisTemplate.delete(getCacheKey(vehicleId));
    }

    @Override
    public boolean exists(UUID vehicleId)
    {
        return Boolean.TRUE.equals(redisTemplate.hasKey(getCacheKey(vehicleId)));
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

    private String getCacheKey(UUID vehicleId)
    {
        return KEY_PREFIX + vehicleId.toString();
    }
}