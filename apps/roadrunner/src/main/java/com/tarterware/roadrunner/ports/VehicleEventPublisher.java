package com.tarterware.roadrunner.ports;

import com.tarterware.roadrunner.components.Vehicle;

public interface VehicleEventPublisher
{

    /**
     * Publish that a new Vehicle has been created.
     *
     * @param vehicle New Vehicle to publish.
     */
    void publishVehicleCreated(Vehicle vehicle, String username);

    /**
     * Publish updated Vehicle information.
     *
     * @param vehicle Vehicle to publish.
     */
    void publishVehicleUpdated(Vehicle vehicle);

    /**
     * Publish that a Vehicle has been deleted.
     *
     * @param vehicleId New Vehicle ID to delete.
     */
    void publishVehicleDeleted(String vehicleId);
}