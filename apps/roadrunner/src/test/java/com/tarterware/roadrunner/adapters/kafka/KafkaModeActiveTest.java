package com.tarterware.roadrunner.adapters.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tarterware.roadrunner.RoadrunnerApplication;
import com.tarterware.roadrunner.messaging.VehiclePositionEvent;
import com.tarterware.roadrunner.ports.VehicleEventPublisher;

@SpringBootTest(
        properties =
            {
                    "com.tarterware.roadrunner.messaging.kafka.enabled=true",
                    "com.tarterware.roadrunner.messaging.redis.enabled=false"
            },
        // Exclude the classes that try to connect to real brokers
        classes = RoadrunnerApplication.class)
@EnableAutoConfiguration(exclude =
    {
            KafkaAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
    })
public class KafkaModeActiveTest
{
    @MockitoBean
    private KafkaTemplate<String, VehiclePositionEvent> kafkaTemplate;

    @Autowired
    private VehicleEventPublisher publisher;

    @Test
    void shouldInjectKafkaImplementation()
    {
        assertThat(publisher).isInstanceOf(KafkaVehicleEventPublisher.class);
    }
}
