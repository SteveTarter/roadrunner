package com.tarterware.roadrunner.adapters.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.messaging.VehicleSimulationStartEvent;
import com.tarterware.roadrunner.components.VehicleManager;

/**
 * Kafka consumer that listens to the simulation-start topic,
 * and triggers VehicleManager to begin simulating the vehicle.
 */
@Component
@ConditionalOnProperty(prefix = "com.tarterware.roadrunner.messaging.kafka", name = "enabled", havingValue = "true")
public class VehicleSimulationStartConsumer
{
    private final VehicleManager vehicleManager;

    private static final Logger log = LoggerFactory.getLogger(VehicleSimulationStartConsumer.class);

    public VehicleSimulationStartConsumer(VehicleManager vehicleManager)
    {
        this.vehicleManager = vehicleManager;
        log.info("VehicleSimulationStartConsumer is ACTIVE");
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".dlq.v1",
            include = {Exception.class}
    )
    @KafkaListener(
            topics = "${com.tarterware.roadrunner.kafka.topic.simulation-start}",
            groupId = "VehicleSimulationStartConsumer-${K8S_POD_NAME:default}")
    public void receive(@Payload VehicleSimulationStartEvent event)
    {
        log.info("Received simulation start event for vehicle ID {}", event.vehicleId());
        try
        {
            vehicleManager.startVehicleSimulation(event.vehicleId(), event.tripPlan(), event.username());
            log.info("Successfully started simulation for vehicle ID {}", event.vehicleId());
        }
        catch (Exception ex)
        {
            log.error("Failed to start simulation for vehicle ID {} at timestamp {}",
                    event.vehicleId(), event.eventTime(), ex);
            throw ex; // Retrigger retry policy / DLT routing
        }
    }
}
