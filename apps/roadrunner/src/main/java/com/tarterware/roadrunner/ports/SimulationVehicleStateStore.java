package com.tarterware.roadrunner.ports;

/**
 * A specialized extension of {@link VehicleStateStore} intended for use by the
 * simulation runner component.
 *
 * <p>
 * This interface acts as a specific "Port" in the hexagonal architecture,
 * allowing the simulation engine (primarily
 * {@link com.tarterware.roadrunner.components.VehicleManager}) to persist and
 * manage the state of vehicles during the simulation lifecycle. By defining
 * this as a separate port, the application can switch between different
 * persistence backends (e.g., Redis or Kafka-backed in-memory) without
 * modifying the core simulation logic.
 * </p>
 *
 * @see VehicleStateStore
 * @see ControllerVehicleStateStore
 * @see com.tarterware.roadrunner.components.VehicleManager
 */
public interface SimulationVehicleStateStore extends VehicleStateStore
{
    // This interface inherits all methods from VehicleStateStore to fulfill
    // the contract required by the simulation runner.
}
