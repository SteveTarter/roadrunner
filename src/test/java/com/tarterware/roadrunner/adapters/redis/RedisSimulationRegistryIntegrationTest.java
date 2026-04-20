package com.tarterware.roadrunner.adapters.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.tarterware.roadrunner.components.Vehicle;
import com.tarterware.roadrunner.configs.RedisConfig;
import com.tarterware.roadrunner.configs.SecurityConfig;
import com.tarterware.roadrunner.models.SimulationSession;
import com.tarterware.roadrunner.ports.SimulationVehicleStateStore;
import com.tarterware.roadrunner.ports.TripPlanRepository;
import com.tarterware.roadrunner.ports.VehicleEventPublisher;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.services.GeocodingService;
import com.tarterware.roadrunner.services.IsochroneService;
import com.tarterware.roadrunner.services.KafkaTopicMetadataService;

import io.fabric8.kubernetes.client.KubernetesClient;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class RedisSimulationRegistryIntegrationTest
{
    @SuppressWarnings("resource")
    @Container
    public static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry)
    {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private RedisSimulationRegistry registry;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private RedisDirectionsCache redisDirectionsCache;

    @MockitoBean
    private TripPlanRepository tripPlanRepository;

    @MockitoBean
    private RedisFeatureCollectionCache redisFeatureCollectionCache;

    @MockitoBean
    private RedisIsochroneCache redisIsochroneCache;

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
    private SecurityFilterChain filterChain;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private SimulationVehicleStateStore vehicleStateStore;

    @MockitoBean
    private VehicleEventPublisher vehicleEventPublisher;

    @MockitoBean
    private KubernetesClient kubernetesClient;

    @MockitoBean
    private KafkaTopicMetadataService kafkaTopicMetadataService;

    @BeforeEach
    void setUp()
    {
        // Essential: Clear Redis before each test so they are independent
        redisTemplate.delete("roadrunner:simulations");

        when(kafkaTopicMetadataService.getTopicRetention(anyString())).thenReturn(Duration.ofDays(7));
    }

    @AfterEach
    void tearDown()
    {
        // Stop all background consumers so they don't scream in the logs
        // while the next test is running.
        kafkaListenerEndpointRegistry.stop();
    }

    @Test
    void testRecordStartAndRetrieval()
    {
        Vehicle vehicle = new Vehicle();
        Instant startTime = Instant.now();

        registry.recordStart(vehicle, startTime);

        List<SimulationSession> sessions = registry.getAllSessions();
        assertEquals(1, sessions.size(), "Should have one session");
        assertEquals(vehicle.getId(), sessions.get(0).getId());
        assertEquals(startTime, sessions.get(0).getStart());
        assertNull(sessions.get(0).getEnd(), "End time should be null for active session");
    }

    @Test
    void testRecordEndUpdatesExistingSession()
    {
        Vehicle vehicle = new Vehicle();
        Instant startTime = Instant.now().minusSeconds(60);
        Instant endTime = Instant.now();

        // Start simulation
        registry.recordStart(vehicle, startTime);

        // End simulation
        registry.recordEnd(vehicle.getId(), endTime);

        // Verify
        List<SimulationSession> sessions = registry.getAllSessions();
        assertEquals(1, sessions.size(), "Should still only have one session entry");
        assertEquals(endTime, sessions.get(0).getEnd(), "End time should be updated");
        assertEquals(startTime, sessions.get(0).getStart(), "Start time should be preserved");
    }

    @Test
    void testChronologicalOrdering()
    {
        Vehicle vehicle1 = new Vehicle();
        Vehicle vehicle2 = new Vehicle();
        Instant now = Instant.now();

        // Record out of order
        registry.recordStart(vehicle2, now); // Newer
        registry.recordStart(vehicle1, now.minusSeconds(100)); // Older

        List<SimulationSession> sessions = registry.getAllSessions();

        assertEquals(2, sessions.size());
        assertEquals(vehicle1.getId(), sessions.get(0).getId(), "Older session should be first (ZSet score logic)");
        assertEquals(vehicle2.getId(), sessions.get(1).getId(), "Newer session should be last");
    }
}
