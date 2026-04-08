package com.tarterware.roadrunner.ports;

/**
 * A specialized extension of {@link VehicleStateStore} intended for use by the
 * controller layer.
 *
 * <p>
 * This interface acts as a specific "Port" in the hexagonal architecture,
 * allowing REST controllers (such as
 * {@link com.tarterware.roadrunner.controllers.VehicleController}) to access
 * the current state of vehicles in the simulation. By separating this into its
 * own interface, the system can provide different implementations (e.g.,
 * Redis-backed vs. In-Memory) based on the active messaging profile without
 * impacting the view layer.
 * </p>
 *
 * @see VehicleStateStore
 * @see RunnerVehicleStateStore
 * @see com.tarterware.roadrunner.controllers.VehicleController
 */
public interface ControllerVehicleStateStore extends VehicleStateStore
{
    // This interface inherits all methods from VehicleStateStore to fulfill
    // the contract required by the view-facing controllers.
}
