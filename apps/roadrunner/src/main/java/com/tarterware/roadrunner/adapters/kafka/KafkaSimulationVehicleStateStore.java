package com.tarterware.roadrunner.adapters.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.adapters.InMemoryVehicleStateStore;
import com.tarterware.roadrunner.ports.SimulationVehicleStateStore;

/**
 * A Kafka-specific implementation of the {@link SimulationVehicleStateStore}
 * port, used by the simulation runner to manage vehicle state. *
 * <p>
 * This class extends {@link InMemoryVehicleStateStore} and implements the
 * {@link SimulationVehicleStateStore} interface. It provides the simulation
 * engine with a mechanism to persist and retrieve the latest vehicle telemetry
 * when the application is configured to use Kafka as the primary messaging
 * backbone.
 * </p>
 * *
 * <p>
 * The component is conditionally activated only when the property
 * {@code com.tarterware.roadrunner.messaging.kafka.enabled} is set to
 * {@code true}. In this mode, it replaces the Redis-based implementation to
 * support the architectural shift toward an event-driven model.
 * </p>
 * * @see InMemoryVehicleStateStore
 * 
 * @see SimulationVehicleStateStore
 * @see com.tarterware.roadrunner.components.VehicleManager
 */
@Component
@ConditionalOnProperty(prefix = "com.tarterware.roadrunner.messaging.kafka", name = "enabled", havingValue = "true")
public class KafkaSimulationVehicleStateStore extends InMemoryVehicleStateStore implements SimulationVehicleStateStore
{
    private static final Logger logger = LoggerFactory.getLogger(KafkaSimulationVehicleStateStore.class);

    // Inherits implementation logic from KafkaVehicleStateStore to satisfy
    // the SimulationVehicleStateStore port requirements.

    @Override
    public void reset()
    {
        logger.info("Resetting the variables");

        super.reset();
    }
}
