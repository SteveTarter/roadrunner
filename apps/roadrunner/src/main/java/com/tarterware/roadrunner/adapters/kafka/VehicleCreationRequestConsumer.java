package com.tarterware.roadrunner.adapters.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.messaging.VehicleCreationRequestEvent;
import com.tarterware.roadrunner.messaging.VehicleSimulationStartEvent;
import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.models.mapbox.Directions;
import com.tarterware.roadrunner.ports.TripPlanRepository;
import com.tarterware.roadrunner.services.DirectionsService;

import java.time.Instant;

/**
 * Kafka consumer that processes vehicle creation request events,
 * fetches routing/directions details (via cache or Mapbox REST API),
 * and triggers simulation startup.
 */
@Component
@ConditionalOnProperty(prefix = "com.tarterware.roadrunner.messaging.kafka", name = "enabled", havingValue = "true")
public class VehicleCreationRequestConsumer
{
    private final DirectionsService directionsService;
    private final TripPlanRepository tripPlanRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${com.tarterware.roadrunner.kafka.topic.simulation-start}")
    private String simulationStartTopic;

    private static final Logger log = LoggerFactory.getLogger(VehicleCreationRequestConsumer.class);

    public VehicleCreationRequestConsumer(
            DirectionsService directionsService,
            TripPlanRepository tripPlanRepository,
            KafkaTemplate<String, Object> kafkaTemplate)
    {
        this.directionsService = directionsService;
        this.tripPlanRepository = tripPlanRepository;
        this.kafkaTemplate = kafkaTemplate;
        log.info("VehicleCreationRequestConsumer is ACTIVE");
    }

    @RetryableTopic(
            attempts = "5",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".dlq.v1",
            include = {Exception.class}
    )
    @KafkaListener(
            topics = "${com.tarterware.roadrunner.kafka.topic.creation-request}",
            groupId = "VehicleCreationRequestConsumer")
    public void receive(@Payload VehicleCreationRequestEvent event)
    {
        log.info("Received creation request event for vehicle ID {}", event.vehicleId());

        // 1. Retrieve TripPlan (with Redis fallback)
        TripPlan tripPlan = null;
        try
        {
            tripPlan = tripPlanRepository.getTripPlan(event.vehicleId());
        }
        catch (Exception ex)
        {
            log.warn("Redis error fetching TripPlan for vehicle ID {}: {}", event.vehicleId(), ex.getMessage());
        }

        if (tripPlan == null)
        {
            log.info("TripPlan not found in Redis, falling back to payload TripPlan for vehicle ID {}", event.vehicleId());
            tripPlan = event.tripPlan();
        }

        if (tripPlan == null)
        {
            throw new IllegalArgumentException("TripPlan is null for vehicle ID " + event.vehicleId());
        }

        // 2. Fetch Directions (caching handled inside directionsService)
        Directions directions = directionsService.getDirectionsForTripPlan(tripPlan);
        if (directions == null)
        {
            throw new IllegalStateException("Failed to retrieve directions from Mapbox API for vehicle ID " + event.vehicleId());
        }

        // 3. Publish SimulationStart event
        VehicleSimulationStartEvent startEvent = new VehicleSimulationStartEvent(
                event.vehicleId(),
                event.username(),
                tripPlan,
                event.colorCode(),
                Instant.now()
        );

        kafkaTemplate.send(simulationStartTopic, event.vehicleId(), startEvent);
        log.info("Successfully fetched directions and requested simulation start for vehicle ID {}", event.vehicleId());
    }
}
