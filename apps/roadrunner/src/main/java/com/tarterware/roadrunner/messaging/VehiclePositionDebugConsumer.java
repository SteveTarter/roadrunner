package com.tarterware.roadrunner.messaging;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

@Component
@ConditionalOnProperty(
        prefix = "com.tarterware.roadrunner.messaging.kafka.debug",
        name = "enabled",
        havingValue = "true")
public class VehiclePositionDebugConsumer
{
    private final AdminClient adminClient;

    private static final Logger log = LoggerFactory.getLogger(VehiclePositionDebugConsumer.class);

    public VehiclePositionDebugConsumer(
            AdminClient adminClient)
    {
        this.adminClient = adminClient;
    }

    @KafkaListener(
            topics = "${com.tarterware.roadrunner.kafka.topic.vehicle-position}",
            groupId = "VehiclePositionDebugConsumer-${K8S_POD_NAME:default}")
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

    @PreDestroy
    public void cleanup()
    {
        String groupId = "VehiclePositionDebugConsumer-${K8S_POD_NAME:default}";
        try
        {
            // Explicitly delete the unique consumer group on pod exit
            adminClient.deleteConsumerGroups(Collections.singletonList(groupId))
                    .all()
                    .get(5, TimeUnit.SECONDS);
            System.out.println("Successfully deleted Kafka consumer group: " + groupId);
        }
        catch (Exception e)
        {
            System.err.println("Failed to delete Kafka consumer group: " + e.getMessage());
        }
    }

}