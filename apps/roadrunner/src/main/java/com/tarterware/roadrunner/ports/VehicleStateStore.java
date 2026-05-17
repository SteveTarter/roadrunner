package com.tarterware.roadrunner.ports;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.tarterware.roadrunner.models.VehicleState;

public interface VehicleStateStore
{

    /**
     * Retrieve a specific VehicleState by its ID.
     *
     * @param vehicleId The ID of the VehicleState to retrieve
     * @return The corresponding VehicleState, or null if not found.
     */
    VehicleState getVehicle(String vehicleId);

    /**
     * Get a map of specified VehicleStates.
     *
     * @param vehicleStateIds Collection of VehicleState IDs to retrieve.
     * @return A Map of VehicleStates by their IDs.
     */
    Map<String, VehicleState> getVehicles(Collection<String> vehicleIds);

    /**
     * Save VehicleState to the VehicleStateStore.
     *
     * @param vehicleState VehicleState to save.
     */
    void saveVehicle(VehicleState vehicleState);

    /**
     * Delete VehicleState from the VehicleStateStore.
     *
     * @param vehicleId ID of VehicleState to delete.
     */
    void deleteVehicle(String vehicleId);

    /**
     * Get a set of active VehicleState IDs.
     *
     * @return A Set of active VehicleState IDs.
     */
    Set<String> getActiveVehicleIds();

    /**
     * Add VehicleState ID to active vehicleState set.
     *
     * @param vehicleState ID of VehicleState to save.
     */
    void addActiveVehicle(String vehicleId);

    /**
     * Remove VehicleState ID from active vehicleState set.
     *
     * @param vehicleState ID of VehicleState to remove.
     */
    void removeActiveVehicle(String vehicleStateId);

    /**
     * Acquire a per-vehicle update lock.
     *
     * @param vehicleId ID of vehicle
     * @return true if the caller acquired the lock, false otherwise.
     */
    boolean tryAcquireUpdateLock(String vehicleId);

    /**
     * Release per-vehicle update lock.
     *
     * @param vehicleId ID of vehicle
     * @return true if the caller acquired the lock, false otherwise.
     */
    void releaseUpdateLock(String vehicleId);

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