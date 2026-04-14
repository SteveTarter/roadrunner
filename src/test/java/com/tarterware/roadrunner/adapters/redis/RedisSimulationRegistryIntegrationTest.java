package com.tarterware.roadrunner.adapters.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.tarterware.roadrunner.models.SimulationSession;
import com.tarterware.roadrunner.ports.TripPlanRepository;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class RedisSimulationRegistryIntegrationTest
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
    private RedisSimulationRegistry registry;

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

    @BeforeEach
    void setUp()
    {
        // Essential: Clear Redis before each test so they are independent
        redisTemplate.delete("roadrunner:simulations");
    }

    @Test
    void testRecordStartAndRetrieval()
    {
        UUID simId = UUID.randomUUID();
        Instant startTime = Instant.now();

        registry.recordStart(simId, startTime);

        List<SimulationSession> sessions = registry.getAllSessions();
        assertEquals(1, sessions.size(), "Should have one session");
        assertEquals(simId, sessions.get(0).getId());
        assertEquals(startTime, sessions.get(0).getStart());
        assertNull(sessions.get(0).getEnd(), "End time should be null for active session");
    }

    @Test
    void testRecordEndUpdatesExistingSession()
    {
        UUID simId = UUID.randomUUID();
        Instant startTime = Instant.now().minusSeconds(60);
        Instant endTime = Instant.now();

        // Start simulation
        registry.recordStart(simId, startTime);

        // End simulation
        registry.recordEnd(simId, endTime);

        // Verify
        List<SimulationSession> sessions = registry.getAllSessions();
        assertEquals(1, sessions.size(), "Should still only have one session entry");
        assertEquals(endTime, sessions.get(0).getEnd(), "End time should be updated");
        assertEquals(startTime, sessions.get(0).getStart(), "Start time should be preserved");
    }

    @Test
    void testChronologicalOrdering()
    {
        UUID sim1 = UUID.randomUUID();
        UUID sim2 = UUID.randomUUID();
        Instant now = Instant.now();

        // Record out of order
        registry.recordStart(sim2, now); // Newer
        registry.recordStart(sim1, now.minusSeconds(100)); // Older

        List<SimulationSession> sessions = registry.getAllSessions();

        assertEquals(2, sessions.size());
        assertEquals(sim1, sessions.get(0).getId(), "Older session should be first (ZSet score logic)");
        assertEquals(sim2, sessions.get(1).getId(), "Newer session should be last");
    }
}
