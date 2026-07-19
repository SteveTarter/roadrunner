package com.tarterware.roadrunner.adapters.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarterware.roadrunner.models.Bookmark;
import com.tarterware.roadrunner.models.SimulationSession;
import com.tarterware.roadrunner.ports.BookmarkRepository;
import com.tarterware.roadrunner.ports.SimulationRegistry;
import com.tarterware.roadrunner.services.KafkaTopicMetadataService;

import jakarta.annotation.PostConstruct;

@Component
public class RedisBookmarkRegistry implements BookmarkRepository
{
    /** The Redis key used to store the sorted set of simulation sessions. */
    private static final String BOOKMARKS_KEY = "roadrunner:bookmarks";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimulationRegistry simulationRegistry;
    private final KafkaTopicMetadataService metadataService;
    private Duration ttl;

    @Value("${com.tarterware.roadrunner.kafka.topic.vehicle-position}")
    private String topicName;

    private static final Logger logger = LoggerFactory.getLogger(RedisBookmarkRegistry.class);

    public RedisBookmarkRegistry(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            SimulationRegistry simulationRegistry,
            KafkaTopicMetadataService metadataService)
    {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.simulationRegistry = simulationRegistry;
        this.metadataService = metadataService;
    }

    @PostConstruct
    public void init()
    {
        this.ttl = metadataService.getTopicRetention(topicName);
    }

    @Override
    public Bookmark saveBookmark(Bookmark bookmark)
    {
        if (bookmark == null)
        {
            throw new IllegalArgumentException("Bookmark cannot be null!");
        }

        if ((bookmark.getVehicleId() == null) || (bookmark.getVehicleId().length() == 0))
        {
            throw new IllegalArgumentException("Bookmark vehicle ID must be specified!");
        }

        // Look up the start time, so the Bookmarks will be naturally ordered.
        Optional<SimulationSession> session = simulationRegistry.getAllSessions().stream()
                .filter(s -> s.getId().equals(bookmark.getVehicleId()))
                .findFirst();

        if (session.isEmpty())
        {
            throw new IllegalArgumentException("Unable to find SimulationSession with ID " + bookmark.getVehicleId());
        }

        bookmark.setStart(session.get().getStart());

        saveToZSet(bookmark);

        return bookmark;
    }

    @Override
    public Bookmark updateBookmark(Bookmark bookmark)
    {
        return saveBookmark(bookmark);
    }

    @Override
    public void deleteBookmark(String vehicleId)
    {
        if (vehicleId == null)
        {
            throw new IllegalArgumentException("Vehicle ID cannot be null!");
        }

        Bookmark bookmark = getBookmark(vehicleId);
        removeFromZSet(bookmark);
    }

    @Override
    public List<Bookmark> getAllBookmarks()
    {
        Set<String> jsonBookmarks = redisTemplate.opsForZSet().range(BOOKMARKS_KEY, 0, -1);
        if (jsonBookmarks == null)
            return new ArrayList<>();

        return jsonBookmarks.stream()
                .map(this::deserialize)
                .collect(Collectors.toList());
    }

    @Override
    public Bookmark getBookmark(String vehicleId)
    {
        if (vehicleId == null)
        {
            throw new IllegalArgumentException("Vehicle ID cannot be null!");
        }

        Optional<Bookmark> bookmark = getAllBookmarks().stream()
                .filter(b -> b.getVehicleId().equals(vehicleId))
                .findFirst();

        if (bookmark.isEmpty())
        {
            throw new IllegalArgumentException("Vehicle ID " + vehicleId + " not found in Bookmarks!");
        }

        return bookmark.get();
    }

    @Override
    public void reset()
    {
        long cutoff = Instant.now().toEpochMilli();
        Long removedCount = redisTemplate.opsForZSet().removeRangeByScore(BOOKMARKS_KEY, 0, cutoff);

        if (removedCount != null && removedCount > 0)
        {
            logger.info("Reset: Evicted {} bookmarks from Redis", removedCount);
        }
    }

    /**
     * Internal helper to serialize a bookmark and add it to the Redis ZSet.
     *
     * @param session the session to persist
     */
    private void saveToZSet(Bookmark bookmark)
    {
        try
        {
            String json = objectMapper.writeValueAsString(bookmark);
            double score = bookmark.getStart();
            redisTemplate.opsForZSet().add(BOOKMARKS_KEY, json, score);
        }
        catch (JsonProcessingException e)
        {
            logger.error("Failed to serialize Bookmark", e);
        }
    }

    /**
     * Internal helper to remove a specific bookmark entry from the Redis ZSet.
     *
     * @param bookmark the bookmark to remove
     */
    private void removeFromZSet(Bookmark bookmark)
    {
        try
        {
            String json = objectMapper.writeValueAsString(bookmark);
            redisTemplate.opsForZSet().remove(BOOKMARKS_KEY, json);
        }
        catch (JsonProcessingException e)
        {
            logger.error("Failed to serialize for removal", e);
        }
    }

    /**
     * Internal helper to deserialize a JSON string into a Bookmark object.
     *
     * @param json the JSON data from Redis
     * @return the deserialized bookmark, or null if deserialization fails
     */
    private Bookmark deserialize(String json)
    {
        try
        {
            return objectMapper.readValue(json, Bookmark.class);
        }
        catch (JsonProcessingException e)
        {
            logger.error("Failed to deserialize Bookmark", e);
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
        Long removedCount = redisTemplate.opsForZSet().removeRangeByScore(BOOKMARKS_KEY, 0, cutoff);

        if (removedCount != null && removedCount > 0)
        {
            logger.info("Maintenance: Evicted {} expired bookmarks from Redis", removedCount);
        }
    }
}
