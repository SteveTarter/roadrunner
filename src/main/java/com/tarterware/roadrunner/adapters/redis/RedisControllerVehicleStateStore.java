package com.tarterware.roadrunner.adapters.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.ports.ControllerVehicleStateStore;

/**
 * Redis-based implementation of the {@link ControllerVehicleStateStore} port. *
 * <p>
 * This component provides the REST controllers and the UI with access to the
 * latest vehicle telemetry persisted in Redis. It is specifically used when the
 * application is configured for Redis-based messaging rather than Kafka.
 * </p>
 * *
 * <p>
 * The component is conditionally activated only when the property
 * {@code com.tarterware.roadrunner.messaging.redis.enabled} is set to
 * {@code true}.
 * </p>
 * * @see RedisVehicleStateStore
 * 
 * @see ControllerVehicleStateStore
 * @see com.tarterware.roadrunner.controllers.VehicleController
 */
@Component
@ConditionalOnProperty(prefix = "com.tarterware.roadrunner.messaging.redis", name = "enabled", havingValue = "true")
public class RedisControllerVehicleStateStore extends RedisVehicleStateStore implements ControllerVehicleStateStore
{

    public RedisControllerVehicleStateStore(RedisTemplate<String, Object> redisTemplate)
    {
        super(redisTemplate);
    }

}
