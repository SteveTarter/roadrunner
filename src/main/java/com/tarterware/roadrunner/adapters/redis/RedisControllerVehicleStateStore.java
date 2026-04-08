package com.tarterware.roadrunner.adapters.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.ports.ControllerVehicleStateStore;

@Component
@ConditionalOnProperty(prefix = "com.tarterware.roadrunner.messaging.redis", name = "enabled", havingValue = "true")
public class RedisControllerVehicleStateStore extends RedisVehicleStateStore implements ControllerVehicleStateStore
{

    public RedisControllerVehicleStateStore(RedisTemplate<String, Object> redisTemplate)
    {
        super(redisTemplate);
    }

}
