package com.tarterware.roadrunner.adapters.kafka;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.components.Vehicle;
import com.tarterware.roadrunner.messaging.VehiclePositionEvent;
import com.tarterware.roadrunner.ports.VehicleEventPublisher;

/**
 * Implementation of the {@link VehicleEventPublisher} port that broadcasts
 * vehicle lifecycle events to a Kafka cluster. *
 * <p>
 * This adapter allows the Roadrunner application to operate in an event-driven
 * architecture by publishing telemetry and state changes to a centralized
 * message bus.
 * </p>
 * *
 * <p>
 * The component is conditionally managed by the Spring context and is only
 * active when the property
 * {@code com.tarterware.roadrunner.messaging.kafka.enabled} is set to
 * {@code true}.
 * </p>
 * * @see VehicleEventPublisher
 * 
 * @see VehiclePositionEvent
 */
@Component
@ConditionalOnProperty(prefix = "com.tarterware.roadrunner.messaging.kafka", name = "enabled", havingValue = "true")
public class KafkaVehicleEventPublisher implements VehicleEventPublisher
{
    private final KafkaTemplate<String, VehiclePositionEvent> kafkaTemplate;

    @Value("${com.tarterware.roadrunner.kafka.topic.vehicle-position}")
    private String topicName;

    private static final Logger logger = LoggerFactory.getLogger(KafkaVehicleEventPublisher.class);

    /**
     * Constructs the publisher with a provided {@link KafkaTemplate}. * @param
     * kafkaTemplate the template used to execute high-level Kafka operations
     */
    public KafkaVehicleEventPublisher(KafkaTemplate<String, VehiclePositionEvent> kafkaTemplate)

    {
        this.kafkaTemplate = kafkaTemplate;
        logger.info("KafkaVehicleEventPublisher is ACTIVE");
    }

    @Override
    public void publishVehicleCreated(Vehicle vehicle)
    {
        publishEvent(vehicle, "CREATED");
    }

    @Override
    public void publishVehicleUpdated(Vehicle vehicle)
    {
        publishEvent(vehicle, "MOVING");
    }

    @Override
    public void publishVehicleDeleted(UUID vehicleId)
    {
        // For deletion, we send a minimal event with the DELETED status
        VehiclePositionEvent deleteEvent = new VehiclePositionEvent(
                vehicleId.toString(),
                Instant.now(),
                -1, // Indicator for terminal event
                -1,
                false, false,
                0, 0, 0, 0,
                "", "",
                "DELETED");

        sendToKafka(vehicleId.toString(), deleteEvent);
    }

    /**
     * Maps a {@link Vehicle} domain object to a {@link VehiclePositionEvent} DTO
     * and triggers the transmission to Kafka. * @param vehicle the domain object to
     * transform
     * 
     * @param status the lifecycle status string (e.g., CREATED, MOVING)
     */
    private void publishEvent(Vehicle vehicle, String status)
    {
        VehiclePositionEvent event = new VehiclePositionEvent(
                vehicle.getId().toString(),
                Instant.now(),
                vehicle.getLastCalculationEpochMillis(), // Using timestamp as a sequence proxy
                vehicle.getLastNsExecutionTime(),
                vehicle.isPositionValid(),
                vehicle.isPositionLimited(),
                vehicle.getDegLatitude(),
                vehicle.getDegLongitude(),
                vehicle.getDegBearing(),
                vehicle.getMetersPerSecond(),
                vehicle.getColorCode(),
                vehicle.getManagerHost(),
                status);

        sendToKafka(vehicle.getId().toString(), event);
    }

    /**
     * Executes the actual message transmission via {@link KafkaTemplate}. *
     * <p>
     * Messages are keyed by the vehicle's UUID string. Keying by vehicleId ensures
     * that all sequential updates for a specific vehicle are routed to the same
     * Kafka partition, thereby guaranteeing ordered delivery to consumers.
     * </p>
     * * @param key the partition key (vehicle ID)
     * 
     * @param event the event payload
     */
    private void sendToKafka(String key, VehiclePositionEvent event)
    {
        logger.debug("Publishing {} event for vehicle {} on topic {}", event.status(), key, topicName);

        // Keying by vehicleId ensures all updates for one vehicle go to the same
        // partition
        kafkaTemplate.send(topicName, key, event)
                .whenComplete((result, ex) ->
                {
                    if (ex != null)
                    {
                        logger.error("Failed to publish event for vehicle {}", key, ex);
                    }
                });
    }
}
