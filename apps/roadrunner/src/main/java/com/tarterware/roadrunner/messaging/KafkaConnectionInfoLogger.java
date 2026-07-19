package com.tarterware.roadrunner.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@ConditionalOnProperty(prefix = "com.tarterware.roadrunner.messaging.kafka", name = "enabled", havingValue = "true")
public class KafkaConnectionInfoLogger
{

    private static final Logger log = LoggerFactory.getLogger(KafkaConnectionInfoLogger.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${com.tarterware.roadrunner.kafka.topic.vehicle-position}")
    private String topic;

    @PostConstruct
    void logConfig()
    {
        log.info("Kafka messaging has been enabled");
        log.info("Kafka bootstrap servers: {}", bootstrapServers);
        log.info("Kafka vehicle-position topic: {}", topic);
    }
}