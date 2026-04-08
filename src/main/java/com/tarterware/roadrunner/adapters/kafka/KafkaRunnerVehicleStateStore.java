package com.tarterware.roadrunner.adapters.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.ports.RunnerVehicleStateStore;

/**
 * A Kafka-specific implementation of the {@link RunnerVehicleStateStore} port,
 * used by the simulation runner to manage vehicle state. *
 * <p>
 * This class extends {@link KafkaVehicleStateStore} and implements the
 * {@link RunnerVehicleStateStore} interface. It provides the simulation engine
 * with a mechanism to persist and retrieve the latest vehicle telemetry when
 * the application is configured to use Kafka as the primary messaging backbone.
 * </p>
 * *
 * <p>
 * The component is conditionally activated only when the property
 * {@code com.tarterware.roadrunner.messaging.kafka.enabled} is set to
 * {@code true}. In this mode, it replaces the Redis-based implementation to
 * support the architectural shift toward an event-driven model.
 * </p>
 * * @see KafkaVehicleStateStore
 * 
 * @see RunnerVehicleStateStore
 * @see com.tarterware.roadrunner.components.VehicleManager
 */
@Component
@ConditionalOnProperty(prefix = "com.tarterware.roadrunner.messaging.kafka", name = "enabled", havingValue = "true")
public class KafkaRunnerVehicleStateStore extends KafkaVehicleStateStore implements RunnerVehicleStateStore
{
    // Inherits implementation logic from KafkaVehicleStateStore to satisfy
    // the RunnerVehicleStateStore port requirements.
}
