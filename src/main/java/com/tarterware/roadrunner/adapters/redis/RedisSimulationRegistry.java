package com.tarterware.roadrunner.adapters.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarterware.roadrunner.models.SimulationSession;
import com.tarterware.roadrunner.ports.SimulationRegistry;
import com.tarterware.roadrunner.services.KafkaTopicMetadataService;

import jakarta.annotation.PostConstruct;

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
    private static final String SESSIONS_KEY = "roadrunner:simulations";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaTopicMetadataService metadataService;
    private Duration ttl;

    @Value("${com.tarterware.roadrunner.kafka.topic.vehicle-position}")
    private String topicName;

    private static final Logger logger = LoggerFactory.getLogger(RedisSimulationRegistry.class);

    /**
     * Constructs a new registry with the necessary Redis and JSON infrastructure.
     *
     * @param redisTemplate the Spring Data Redis template for string operations
     * @param objectMapper  the Jackson mapper for SimulationSession serialization
     */
    public RedisSimulationRegistry(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            KafkaTopicMetadataService metadataService)
    {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.metadataService = metadataService;
    }

    @PostConstruct
    public void init()
    {
        this.ttl = metadataService.getTopicRetention(topicName);
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
        Set<String> jsonSessions = redisTemplate.opsForZSet().range(SESSIONS_KEY, 0, -1);
        if (jsonSessions == null)
            return new ArrayList<>();

        return jsonSessions.stream()
                .map(this::deserialize)
                .collect(Collectors.toList());
    }

    @Override
    public void reset()
    {
        long cutoff = Instant.now().toEpochMilli();
        Long removedCount = redisTemplate.opsForZSet().removeRangeByScore(SESSIONS_KEY, 0, cutoff);

        if (removedCount != null && removedCount > 0)
        {
            logger.info("Reset: Evicted {} simulation sessions from Redis", removedCount);
        }
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
            redisTemplate.opsForZSet().add(SESSIONS_KEY, json, score);
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
            redisTemplate.opsForZSet().remove(SESSIONS_KEY, json);
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

    /**
     * Periodically prunes sessions that have aged beyond the Kafka retention
     * period. Running this once a minute is sufficient to prevent the ZSet from
     * growing unbounded without impacting the performance of recordStart.
     */
    @Scheduled(fixedRate = 60000) // 1 minute
    public void pruneExpiredSessions()
    {
        if (ttl == null)
        {
            return;
        }

        // The cutoff is: Now - Retention Duration + 1 minute
        // The intent: remove entry from registry before it is removed by expiring
        // retention.
        long cutoff = Instant.now().minus(ttl).plus(Duration.ofMinutes(1)).toEpochMilli();

        // Remove everything from score 0 up to our cutoff timestamp
        Long removedCount = redisTemplate.opsForZSet().removeRangeByScore(SESSIONS_KEY, 0, cutoff);

        if (removedCount != null && removedCount > 0)
        {
            logger.info("Maintenance: Evicted {} expired simulation sessions from Redis", removedCount);
        }
    }
}
