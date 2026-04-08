package com.tarterware.roadrunner.adapters.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.ports.ControllerVehicleStateStore;

/**
 * A Kafka-specific implementation of the {@link ControllerVehicleStateStore}
 * that provides vehicle state data to the controller layer. *
 * <p>
 * This class extends {@link KafkaVehicleStateStore} and implements the
 * {@link ControllerVehicleStateStore} port, allowing the UI and REST
 * controllers to access the current state of vehicles when the application is
 * running in Kafka mode.
 * </p>
 * *
 * <p>
 * The component is only registered in the Spring application context if the
 * property {@code com.tarterware.roadrunner.messaging.kafka.enabled} is set to
 * {@code true}. This ensures that the Kafka-backed store is used for the view
 * layer instead of the Redis-backed equivalent during the Kafka migration
 * phase.
 * </p>
 * * @see KafkaVehicleStateStore
 * 
 * @see ControllerVehicleStateStore
 * @see com.tarterware.roadrunner.controllers.VehicleController
 */
@Component
@ConditionalOnProperty(prefix = "com.tarterware.roadrunner.messaging.kafka", name = "enabled", havingValue = "true")
public class KafkaControllerVehicleStateStore extends KafkaVehicleStateStore implements ControllerVehicleStateStore
{
    // This class inherits all implementation logic from KafkaVehicleStateStore
    // to fulfill the ControllerVehicleStateStore port requirements.
}
