package com.tarterware.roadrunner.controllers;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import com.tarterware.roadrunner.ports.SimulationRegistry;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SimulationSessionControllerIntegrationTest
{

    @Container
    public static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry)
    {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private MockMvc mockMvc;

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

    @BeforeEach
    void setUp()
    {
        // Clean the specific key before each test
        // redisTemplate.delete("roadrunner:simulations");
    }

    @Test
    void shouldReturnChronologicalSimulationSessions() throws Exception
    {
        // Arrange: Record two sessions in the registry
        UUID simId1 = UUID.randomUUID();
        UUID simId2 = UUID.randomUUID();
        Instant now = Instant.now();

        // Sim 1 started 10 minutes ago and finished 5 minutes ago
        simulationRegistry.recordStart(simId1, now.minusSeconds(600));
        simulationRegistry.recordEnd(simId1, now.minusSeconds(300));

        // Sim 2 started 2 minutes ago and is still active (end is null)
        simulationRegistry.recordStart(simId2, now.minusSeconds(120));

        // Act & Assert
        mockMvc.perform(get("/api/vehicle/simulation-sessions")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Verify the length of the nested list
                .andExpect(jsonPath("$._embedded.simulationSessions.length()").value(2))

                // Verify the first session (ID and End time exists)
                .andExpect(jsonPath("$._embedded.simulationSessions[0].id").value(simId1.toString()))
                .andExpect(jsonPath("$._embedded.simulationSessions[0].end").exists())

                // Verify the second session (ID and End time is null)
                .andExpect(jsonPath("$._embedded.simulationSessions[1].id").value(simId2.toString()))
                .andExpect(jsonPath("$._embedded.simulationSessions[1].end").value(nullValue()));
    }
}