package com.tarterware.roadrunner.adapters.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tarterware.roadrunner.RoadrunnerApplication;
import com.tarterware.roadrunner.ports.VehicleEventPublisher;

@SpringBootTest(
        properties =
            {
                    "com.tarterware.roadrunner.messaging.kafka.enabled=false",
                    "com.tarterware.roadrunner.messaging.redis.enabled=true",
                    "com.tarterware.redis.password=dummy-password",
                    "mapbox.api.key=dummy-key",
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080"
            },
        // Exclude the classes that try to connect to real brokers
        classes = RoadrunnerApplication.class)
@EnableAutoConfiguration(exclude =
    {
            KafkaAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
    })
public class RedisModeActiveTest
{
    @MockitoBean
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private VehicleEventPublisher publisher;

    @Test
    void shouldInjectRedisImplementation()
    {
        assertThat(publisher).isInstanceOf(RedisVehicleEventPublisher.class);
    }
}
