package com.tarterware.roadrunner.adapters.redis;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.ports.TripPlanRepository;

@Component
public class RedisTripPlanRepository implements TripPlanRepository
{

    public static final String TRIP_PLAN_KEY = "TripPlan";

    private final RedisTemplate<String, Object> redisTemplate;

    private static final Logger logger = LoggerFactory.getLogger(RedisTripPlanRepository.class);

    public RedisTripPlanRepository(RedisTemplate<String, Object> redisTemplate)
    {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public TripPlan getTripPlan(UUID vehicleId)
    {
        Object value = redisTemplate.opsForHash().get(TRIP_PLAN_KEY, vehicleId.toString());
        return (value instanceof TripPlan tripPlan) ? tripPlan : null;
    }

    @Override
    public void saveTripPlan(UUID vehicleId, TripPlan tripPlan)
    {
        redisTemplate.opsForHash().put(TRIP_PLAN_KEY, vehicleId.toString(), tripPlan);
    }

    @Override
    public void deleteTripPlan(UUID vehicleId)
    {
        redisTemplate.opsForHash().delete(TRIP_PLAN_KEY, vehicleId.toString());
    }

    @Override
    public boolean exists(UUID vehicleId)
    {
        Boolean exists = redisTemplate.opsForHash().hasKey(TRIP_PLAN_KEY, vehicleId.toString());
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void reset()
    {
        logger.info("Resetting the variables");

        redisTemplate.delete(TRIP_PLAN_KEY);
    }
}