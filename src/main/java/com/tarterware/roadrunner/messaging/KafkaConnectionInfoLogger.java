package com.tarterware.roadrunner.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class KafkaConnectionInfoLogger
{

    private static final Logger log = LoggerFactory.getLogger(KafkaConnectionInfoLogger.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${roadrunner.kafka.topic.vehicle-position}")
    private String topic;

    @PostConstruct
    void logConfig()
    {
        log.info("Kafka bootstrap servers: {}", bootstrapServers);
        log.info("Kafka vehicle-position topic: {}", topic);
    }
}