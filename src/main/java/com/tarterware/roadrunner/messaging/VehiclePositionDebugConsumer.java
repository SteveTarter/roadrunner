package com.tarterware.roadrunner.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "com.tarterware.roadrunner.messaging.kafka", name = "enabled", havingValue = "true")
public class VehiclePositionDebugConsumer
{

    private static final Logger log = LoggerFactory.getLogger(VehiclePositionDebugConsumer.class);

    @KafkaListener(
            topics = "${com.tarterware.roadrunner.kafka.topic.vehicle-position}",
            groupId = "VehiclePositionDebugConsumer")
    public void receive(@Payload
    VehiclePositionEvent event)
    {
        log.debug("Kafka Event [{}]: Vehicle {} is at ({}, {}) heading {} degrees at {} m/s. Sequence: {}",
                event.status(),
                event.vehicleId(),
                event.latitude(),
                event.longitude(),
                event.heading(),
                event.speed(),
                event.sequenceNumber());

        // Additional debug logic for terminal events
        if ("DELETED".equals(event.status()))
        {
            log.debug("Vehicle {} has been decommissioned.", event.vehicleId());
        }
    }
}