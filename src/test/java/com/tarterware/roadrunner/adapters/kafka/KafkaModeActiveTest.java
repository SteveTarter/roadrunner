package com.tarterware.roadrunner.adapters.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.tarterware.roadrunner.ports.VehicleEventPublisher;

@SpringBootTest(properties =
    {
            "com.tarterware.roadrunner.messaging.kafka.enabled=true",
            "com.tarterware.roadrunner.messaging.redis.enabled=false"
    })
public class KafkaModeActiveTest
{
    @Autowired
    private VehicleEventPublisher publisher;

    @Test
    void shouldInjectKafkaImplementation()
    {
        assertThat(publisher).isInstanceOf(KafkaVehicleEventPublisher.class);
    }
}
