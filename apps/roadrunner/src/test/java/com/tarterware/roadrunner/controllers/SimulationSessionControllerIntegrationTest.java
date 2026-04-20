package com.tarterware.roadrunner.controllers;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.tarterware.roadrunner.adapters.redis.RedisDirectionsCache;
import com.tarterware.roadrunner.adapters.redis.RedisFeatureCollectionCache;
import com.tarterware.roadrunner.adapters.redis.RedisIsochroneCache;
import com.tarterware.roadrunner.adapters.redis.RedisTripPlanRepository;
import com.tarterware.roadrunner.components.Vehicle;
import com.tarterware.roadrunner.configs.RedisConfig;
import com.tarterware.roadrunner.configs.SecurityConfig;
import com.tarterware.roadrunner.ports.SimulationRegistry;
import com.tarterware.roadrunner.ports.SimulationVehicleStateStore;
import com.tarterware.roadrunner.ports.VehicleEventPublisher;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.services.GeocodingService;
import com.tarterware.roadrunner.services.IsochroneService;
import com.tarterware.roadrunner.services.KafkaTopicMetadataService;

import io.fabric8.kubernetes.client.KubernetesClient;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SimulationSessionControllerIntegrationTest
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
    private MockMvc mockMvc;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private SimulationRegistry simulationRegistry;

    // @Autowired
    // private RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    private RedisDirectionsCache redisDirectionsCache;

    @MockitoBean
    private RedisFeatureCollectionCache redisFeatureCollectionCache;

    @MockitoBean
    private RedisIsochroneCache redisIsochroneCache;

    @MockitoBean
    private RedisTripPlanRepository redisTripPlanRepository;

    @SuppressWarnings("unused")
    @Autowired
    private StringRedisTemplate redisTemplate;

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
    void shouldReturnChronologicalSimulationSessions() throws Exception
    {
        // Arrange: Record two sessions in the registry
        Vehicle vehicle1 = new Vehicle();
        Vehicle vehicle2 = new Vehicle();
        Instant now = Instant.now();

        // Sim 1 started 10 minutes ago and finished 5 minutes ago
        simulationRegistry.recordStart(vehicle1, now.minusSeconds(600));
        simulationRegistry.recordEnd(vehicle1.getId(), now.minusSeconds(300));

        // Sim 2 started 2 minutes ago and is still active (end is null)
        simulationRegistry.recordStart(vehicle2, now.minusSeconds(120));

        // Act & Assert
        mockMvc.perform(get("/api/vehicle/simulation-sessions")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Verify the length of the nested list
                .andExpect(jsonPath("$._embedded.simulationSessions.length()").value(2))

                // Verify the first session (ID and End time exists)
                .andExpect(jsonPath("$._embedded.simulationSessions[0].id").value(vehicle1.getId().toString()))
                .andExpect(jsonPath("$._embedded.simulationSessions[0].end").exists())

                // Verify the second session (ID and End time is null)
                .andExpect(jsonPath("$._embedded.simulationSessions[1].id").value(vehicle2.getId().toString()))
                .andExpect(jsonPath("$._embedded.simulationSessions[1].end").value(nullValue()));
    }
}