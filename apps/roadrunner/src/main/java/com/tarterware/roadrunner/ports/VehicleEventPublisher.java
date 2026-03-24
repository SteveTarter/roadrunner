package com.tarterware.roadrunner.ports;

import java.util.UUID;

import com.tarterware.roadrunner.components.Vehicle;

public interface VehicleEventPublisher
{

    /**
     * Publish that a new Vehicle has been created.
     *
     * @param vehicle New Vehicle to publish.
     */
    void publishVehicleCreated(Vehicle vehicle);

    /**
     * Publish updated Vehicle information.
     *
     * @param vehicle Vehicle to publish.
     */
    void publishVehicleUpdated(Vehicle vehicle);

    /**
     * Publish that a Vehicle has been deleted.
     *
     * @param vehicle New Vehicle to publish.
     */
    void publishVehicleDeleted(UUID vehicleId);
}