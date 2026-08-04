package com.tarterware.roadrunner.adapters.redis;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.ports.TripPlanRepository;
import com.tarterware.roadrunner.services.KafkaTopicMetadataService;

import com.tarterware.roadrunner.models.TripPlanEntity;
import com.tarterware.roadrunner.ports.TripPlanEntityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;

@Component
public class RedisTripPlanRepository implements TripPlanRepository
{
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTopicMetadataService metadataService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TripPlanEntityRepository tripPlanEntityRepository;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ObjectMapper objectMapper;
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
    public TripPlan getTripPlan(String vehicleId)
    {
        String cacheKey = getCacheKey(vehicleId);

        try
        {
            Object value = redisTemplate.opsForValue().get(cacheKey);

            if (value instanceof TripPlan)
            {
                // Update the ttl to this latest access time.
                redisTemplate.expire(cacheKey, ttl);

                TripPlan tripPlan = (TripPlan) value;
                return tripPlan;
            }
        }
        catch (Exception ex)
        {
            logger.warn("Failed to get TripPlan from Redis: {}", ex.getMessage());
        }

        return null;
    }

    @Override
    public void saveTripPlan(String vehicleId, TripPlan tripPlan)
    {
        if (tripPlan == null)
        {
            return;
        }

        try
        {
            redisTemplate.opsForValue().set(getCacheKey(vehicleId), tripPlan, ttl);
        }
        catch (Exception ex)
        {
            logger.warn("Failed to save TripPlan to Redis: {}", ex.getMessage());
        }

        // Also persist to PostGIS
        if (tripPlanEntityRepository != null && objectMapper != null)
        {
            try
            {
                String stopsJson = objectMapper.writeValueAsString(tripPlan.getListStops());
                TripPlanEntity entity = new TripPlanEntity();
                entity.setVehicleId(vehicleId);
                entity.setStops(stopsJson);
                tripPlanEntityRepository.save(entity);
            }
            catch (Exception ex)
            {
                logger.error("Failed to save TripPlanEntity to PostGIS", ex);
            }
        }
    }

    @Override
    public void deleteTripPlan(String vehicleId)
    {
        try
        {
            redisTemplate.delete(getCacheKey(vehicleId));
        }
        catch (Exception ex)
        {
            logger.warn("Failed to delete TripPlan from Redis: {}", ex.getMessage());
        }
    }

    @Override
    public boolean exists(String vehicleId)
    {
        try
        {
            return Boolean.TRUE.equals(redisTemplate.hasKey(getCacheKey(vehicleId)));
        }
        catch (Exception ex)
        {
            logger.warn("Failed to check TripPlan existence in Redis: {}", ex.getMessage());
            return false;
        }
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

    private String getCacheKey(String vehicleId)
    {
        return KEY_PREFIX + vehicleId;
    }
}