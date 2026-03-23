package com.tarterware.roadrunner.adapters.redis;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.components.Vehicle;
import com.tarterware.roadrunner.ports.VehicleEventPublisher;

@Component
public class RedisVehicleEventPublisher implements VehicleEventPublisher
{
    private final RedisTemplate<String, Object> redisTemplate;

    private static final Logger logger = LoggerFactory.getLogger(RedisVehicleEventPublisher.class);

    public RedisVehicleEventPublisher(RedisTemplate<String, Object> redisTemplate)
    {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void publishVehicleCreated(Vehicle vehicle)
    {
        logger.info("Vehicle created: {}", vehicle);
        // TODO Add real implementation
    }

    @Override
    public void publishVehicleUpdated(Vehicle vehicle)
    {
        // TODO Add real implementation
    }

    @Override
    public void publishVehicleDeleted(UUID vehicleId)
    {
        logger.info("Vehicle ID {} deleted", vehicleId);
        // TODO Add real implementation
    }

}
