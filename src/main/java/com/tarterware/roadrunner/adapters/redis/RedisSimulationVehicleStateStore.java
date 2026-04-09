package com.tarterware.roadrunner.adapters.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.ports.SimulationVehicleStateStore;

/**
 * Redis-based implementation of the {@link SimulationVehicleStateStore} port.
 *
 * <p>
 * This component provides the simulation runner (e.g.,
 * {@link com.tarterware.roadrunner.components.VehicleManager}) with a mechanism
 * to persist and retrieve the latest vehicle telemetry within a Redis data
 * store. It is utilized when the application is configured for Redis-based
 * messaging rather than Kafka.
 * </p>
 *
 * <p>
 * The component is conditionally registered in the Spring application context
 * only when the property
 * {@code com.tarterware.roadrunner.messaging.redis.enabled} is set to
 * {@code true}.
 * </p>
 *
 * @see RedisVehicleStateStore
 * @see SimulationVehicleStateStore
 * @see com.tarterware.roadrunner.components.VehicleManager
 */
@Component
@ConditionalOnProperty(prefix = "com.tarterware.roadrunner.messaging.redis", name = "enabled", havingValue = "true")
public class RedisSimulationVehicleStateStore extends RedisVehicleStateStore implements SimulationVehicleStateStore
{

    public RedisSimulationVehicleStateStore(RedisTemplate<String, Object> redisTemplate)
    {
        super(redisTemplate);
    }

}
