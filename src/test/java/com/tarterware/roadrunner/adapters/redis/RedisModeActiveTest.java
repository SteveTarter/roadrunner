package com.tarterware.roadrunner.adapters.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.tarterware.roadrunner.ports.VehicleEventPublisher;

@SpringBootTest(properties =
    {
            "com.tarterware.roadrunner.messaging.kafka.enabled=false",
            "com.tarterware.roadrunner.messaging.redis.enabled=true"
    })
public class RedisModeActiveTest
{
    @Autowired
    private VehicleEventPublisher publisher;

    @Test
    void shouldInjectRedisImplementation()
    {
        assertThat(publisher).isInstanceOf(RedisVehicleEventPublisher.class);
    }
}
