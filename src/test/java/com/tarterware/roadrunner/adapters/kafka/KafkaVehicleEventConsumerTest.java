package com.tarterware.roadrunner.adapters.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tarterware.roadrunner.configs.NoOpSchedulerConfig;
import com.tarterware.roadrunner.configs.RedisConfig;
import com.tarterware.roadrunner.configs.SecurityConfig;
import com.tarterware.roadrunner.messaging.VehiclePositionEvent;
import com.tarterware.roadrunner.models.VehicleState;
import com.tarterware.roadrunner.ports.ControllerVehicleStateStore;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.services.GeocodingService;
import com.tarterware.roadrunner.services.IsochroneService;

import io.fabric8.kubernetes.client.KubernetesClient;

@SpringBootTest(
        properties =
            {
                    "com.tarterware.roadrunner.messaging.kafka.enabled=true",
                    "com.tarterware.roadrunner.messaging.redis.enabled=false",
            })
@Import(NoOpSchedulerConfig.class)
public class KafkaVehicleEventConsumerTest
{
    @MockitoBean
    private DirectionsService directionsService;

    @MockitoBean
    private GeocodingService geocodingService;

    @MockitoBean
    private IsochroneService isochroneService;

    @MockitoBean
    private SecurityConfig securityConfig;

    @MockitoBean
    private RedisConfig redisConfig;

    @MockitoBean
    private LettuceConnectionFactory redisStandAloneConnectionFactory;

    @MockitoBean
    private SecurityFilterChain filterChain;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    private KubernetesClient kubernetesClient;

    @MockitoBean
    private KafkaTemplate<String, VehiclePositionEvent> kafkaTemplate;

    @MockitoBean
    private SetOperations<String, Object> mockSetOperations;

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
