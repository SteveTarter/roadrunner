package com.tarterware.roadrunner.adapters.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.tarterware.roadrunner.configs.NoOpSchedulerConfig;
import com.tarterware.roadrunner.messaging.VehiclePositionEvent;
import com.tarterware.roadrunner.models.VehicleState;
import com.tarterware.roadrunner.ports.ControllerVehicleStateStore;

@SpringBootTest(
        properties =
            {
                    "com.tarterware.roadrunner.messaging.kafka.enabled=true",
                    "com.tarterware.roadrunner.messaging.redis.enabled=false",
            })
@Import(NoOpSchedulerConfig.class)
public class KafkaVehicleEventConsumerTest
{
    @Autowired
    private ControllerVehicleStateStore stateStore;

    @Autowired
    private KafkaVehicleEventConsumer kafkaConsumer;

    @Test
    void shouldIgnoreStaleEvents()
    {
        UUID id = UUID.randomUUID();
        VehiclePositionEvent inital = new VehiclePositionEvent(
                id.toString(),
                Instant.now(),
                2000L,
                10L,
                true,
                false,
                32.0,
                -97.0,
                90.0,
                5.0,
                "Yellow",
                "Host",
                "CREATED");

        kafkaConsumer.receive(inital);

        // Update event to change position and back up in time
        VehiclePositionEvent stale = new VehiclePositionEvent(
                id.toString(),
                Instant.now(),
                1000L,
                10L,
                true,
                false,
                33.0,
                -98.0,
                90.0,
                5.0,
                "Yellow",
                "Host",
                "MOVING");

        kafkaConsumer.receive(stale);

        // Get the vehicle state from the state store and ensure it hasn't changed
        VehicleState state = stateStore.getVehicle(id);
        assertEquals(32.0, state.getDegLatitude(), "State should remain at the newer coordinate");
    }
}
