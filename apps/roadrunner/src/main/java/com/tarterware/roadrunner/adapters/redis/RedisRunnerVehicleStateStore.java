package com.tarterware.roadrunner.adapters.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.ports.RunnerVehicleStateStore;

@Component
@ConditionalOnProperty(prefix = "com.tarterware.roadrunner.messaging.redis", name = "enabled", havingValue = "true")
public class RedisRunnerVehicleStateStore extends RedisVehicleStateStore implements RunnerVehicleStateStore
{

    public RedisRunnerVehicleStateStore(RedisTemplate<String, Object> redisTemplate)
    {
        super(redisTemplate);
        // TODO Auto-generated constructor stub
    }

}
