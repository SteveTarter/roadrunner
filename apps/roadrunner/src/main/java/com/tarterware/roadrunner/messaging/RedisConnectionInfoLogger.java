package com.tarterware.roadrunner.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@ConditionalOnProperty(prefix = "roadrunner.messaging.redis", name = "enabled", havingValue = "true")
public class RedisConnectionInfoLogger
{

    private static final Logger log = LoggerFactory.getLogger(RedisConnectionInfoLogger.class);

    @Value("${com.tarterware.redis.host}")
    private String redisHost;

    @Value("${com.tarterware.redis.port}")
    private String redisPort;

    @PostConstruct
    void logConfig()
    {
        log.info("Redis messaging has been enabled");
        log.info("Redis host: {}", redisHost);
        log.info("Redis port: {}", redisPort);
    }
}