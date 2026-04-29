package com.tarterware.roadrunner.services;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tarterware.roadrunner.models.VehicleState;

@Service
public class PlaybackResultCache
{
    private final Cache<String, List<VehicleState>> queryResults;
    private static final Logger logger = LoggerFactory.getLogger(PlaybackResultCache.class);

    public PlaybackResultCache(
            @Value("${com.tarterware.roadrunner.playback-cache-timeout}")
            Duration playbackCacheTimeout,
            @Value("${com.tarterware.roadrunner.playback-cache-size}")
            int playbackCacheSize)
    {
        logger.info("Starting with maximum size of {}, timeout of {}", playbackCacheSize, playbackCacheTimeout);

        this.queryResults = Caffeine.newBuilder()
                .expireAfterAccess(playbackCacheTimeout)
                .maximumSize(playbackCacheSize)
                .build();
    }

    public List<VehicleState> get(String timestamp, long windowMs)
    {
        return queryResults.getIfPresent(timestamp + "_" + windowMs);
    }

    public void put(String timestamp, long windowMs, List<VehicleState> results)
    {
        queryResults.put(timestamp + "_" + windowMs, results);
    }
}