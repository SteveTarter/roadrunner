package com.tarterware.roadrunner.ports;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.tarterware.roadrunner.components.Vehicle;

public interface VehicleStateStore
{

    /**
     * Retrieve a specific Vehicle by its UUID.
     *
     * @param uuid The UUID of the Vehicle.
     * @return The corresponding Vehicle, or null if not found.
     */
    Vehicle getVehicle(UUID vehicleId);

    /**
     * Get a map of specified Vehicles.
     *
     * @param vehicleIds Collection of Vehicle UUIDs to retrieve.
     * @return A Map of Vehicles by their UUIDs.
     */
    Map<UUID, Vehicle> getVehicles(Collection<UUID> vehicleIds);

    /**
     * Save Vehicle to the VehicleStateStore.
     *
     * @param vehicle Vehicle to save.
     */
    void saveVehicle(Vehicle vehicle);

    /**
     * Delete Vehicle from the VehicleStateStore.
     *
     * @param vehicle Vehicle to save.
     */
    void deleteVehicle(UUID vehicleId);

    /**
     * Get a set of active Vehicle UUIDs.
     *
     * @return A Set of active Vehicle UUIDs.
     */
    Set<UUID> getActiveVehicleIds();

    /**
     * Add Vehicle UUID to active vehicle set.
     *
     * @param vehicleId UUID of Vehicle to save.
     */
    void addActiveVehicle(UUID vehicleId);

    /**
     * Remove Vehicle UUID from active vehicle set.
     *
     * @param vehicleId UUID of Vehicle to remove.
     */
    void removeActiveVehicle(UUID vehicleId);

    /**
     * Acquire a per-vehicle update lock.
     *
     * @param vehicleId UUID of vehicle
     * @return true if the caller acquired the lock, false otherwise.
     */
    boolean tryAcquireUpdateLock(UUID vehicleId);

    /**
     * Release per-vehicle update lock.
     *
     * @param vehicleId UUID of vehicle
     * @return true if the caller acquired the lock, false otherwise.
     */
    void releaseUpdateLock(UUID vehicleId);

    /**
     * Get the count of active vehicles.
     *
     * @return true if the caller acquired the lock, false otherwise.
     */
    long getActiveVehicleCount();

    /**
     * Reset the VehicleStateStore, clearing all resources.
     */
    void reset();
}