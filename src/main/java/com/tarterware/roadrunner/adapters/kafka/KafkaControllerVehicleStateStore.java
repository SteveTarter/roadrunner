package com.tarterware.roadrunner.adapters.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.adapters.InMemoryVehicleStateStore;
import com.tarterware.roadrunner.ports.ControllerVehicleStateStore;

/**
 * A Kafka-specific implementation of the {@link ControllerVehicleStateStore}
 * that provides vehicle state data to the controller layer. *
 * <p>
 * This class extends {@link InMemoryVehicleStateStore} and implements the
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
 * * @see InMemoryVehicleStateStore
 * 
 * @see ControllerVehicleStateStore
 * @see com.tarterware.roadrunner.controllers.VehicleController
 */
@Component
@ConditionalOnProperty(prefix = "com.tarterware.roadrunner.messaging.kafka", name = "enabled", havingValue = "true")
public class KafkaControllerVehicleStateStore extends InMemoryVehicleStateStore implements ControllerVehicleStateStore
{
    private static final Logger logger = LoggerFactory.getLogger(KafkaControllerVehicleStateStore.class);

    // This class inherits all implementation logic from KafkaVehicleStateStore
    // to fulfill the ControllerVehicleStateStore port requirements.

    @Override
    public void reset()
    {
        logger.info("Resetting the variables");

        super.reset();
    }
}
