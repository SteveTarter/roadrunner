package com.tarterware.roadrunner.adapters.redis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarterware.roadrunner.models.SimulationSession;
import com.tarterware.roadrunner.ports.SimulationRegistry;

/**
 * Redis implementation of the {@link SimulationRegistry} port.
 *
 * <p>
 * This adapter persists simulation session metadata using a Redis Sorted Set
 * (ZSet). Sessions are stored as serialized JSON strings, with the simulation
 * start time (epoch milliseconds) used as the ZSet score. This ensures that the
 * simulation history is naturally ordered for chronological retrieval by the
 * GUI.
 * </p>
 *
 * @see SimulationRegistry
 * @see SimulationSession
 */
@Component
public class RedisSimulationRegistry implements SimulationRegistry
{
    /** The Redis key used to store the sorted set of simulation sessions. */
    private static final String REDIS_KEY = "roadrunner:simulations";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Logger logger = LoggerFactory.getLogger(RedisSimulationRegistry.class);

    /**
     * Constructs a new registry with the necessary Redis and JSON infrastructure.
     *
     * @param redisTemplate the Spring Data Redis template for string operations
     * @param objectMapper  the Jackson mapper for SimulationSession serialization
     */
    public RedisSimulationRegistry(StringRedisTemplate redisTemplate, ObjectMapper objectMapper)
    {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void recordStart(UUID vehicleID, Instant startTime)
    {
        SimulationSession session = new SimulationSession();
        session.setId(vehicleID);
        session.setStart(startTime);
        session.setEnd(null); // Explicitly null for active sessions

        saveToZSet(session);
        logger.info("Recorded start of simulation session: {}", vehicleID);
    }

    @Override
    public void recordEnd(UUID vehicleID, Instant endTime)
    {
        // Find the existing session in the list to get its start time
        List<SimulationSession> sessions = getAllSessions();
        sessions.stream()
                .filter(s -> s.getId().equals(vehicleID))
                .findFirst()
                .ifPresent(session ->
                {
                    // Remove the old record (the one with end=null)
                    removeFromZSet(session);

                    // Update with the end time and save
                    session.setEnd(endTime);
                    saveToZSet(session);
                    logger.info("Recorded end of simulation session: {}", vehicleID);
                });
    }

    @Override
    public List<SimulationSession> getAllSessions()
    {
        Set<String> jsonSessions = redisTemplate.opsForZSet().range(REDIS_KEY, 0, -1);
        if (jsonSessions == null)
            return new ArrayList<>();

        return jsonSessions.stream()
                .map(this::deserialize)
                .collect(Collectors.toList());
    }

    /**
     * Internal helper to serialize a session and add it to the Redis ZSet.
     *
     * @param session the session to persist
     */
    private void saveToZSet(SimulationSession session)
    {
        try
        {
            String json = objectMapper.writeValueAsString(session);
            double score = session.getStart().toEpochMilli();
            redisTemplate.opsForZSet().add(REDIS_KEY, json, score);
        }
        catch (JsonProcessingException e)
        {
            logger.error("Failed to serialize SimulationSession", e);
        }
    }

    /**
     * Internal helper to remove a specific session entry from the Redis ZSet.
     *
     * @param session the session to remove
     */
    private void removeFromZSet(SimulationSession session)
    {
        try
        {
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForZSet().remove(REDIS_KEY, json);
        }
        catch (JsonProcessingException e)
        {
            logger.error("Failed to serialize for removal", e);
        }
    }

    /**
     * Internal helper to deserialize a JSON string into a SimulationSession object.
     *
     * @param json the JSON data from Redis
     * @return the deserialized session, or null if deserialization fails
     */
    @SuppressWarnings("unused")
    private SimulationSession deserialize(String json)
    {
        try
        {
            return objectMapper.readValue(json, SimulationSession.class);
        }
        catch (JsonProcessingException e)
        {
            logger.error("Failed to deserialize SimulationSession", e);
            return null;
        }
    }
}
