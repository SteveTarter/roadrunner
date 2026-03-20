package com.tarterware.roadrunner.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class VehiclePositionDebugConsumer
{

    private static final Logger log = LoggerFactory.getLogger(VehiclePositionDebugConsumer.class);

    @KafkaListener(topics = "${roadrunner.kafka.topic.vehicle-position}", groupId = "${spring.kafka.consumer.group-id}")
    public void receive(String payload)
    {
        log.info("Kafka raw vehicle event received: {}", payload);
    }
}