package com.tarterware.roadrunner.ports;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.tarterware.roadrunner.models.VehicleState;

public interface VehicleStateStore
{

    /**
     * Retrieve a specific VehicleState by its UUID.
     *
     * @param uuid The UUID of the VehicleState.
     * @return The corresponding VehicleState, or null if not found.
     */
    VehicleState getVehicle(UUID vehicleId);

    /**
     * Get a map of specified VehicleStates.
     *
     * @param vehicleStateIds Collection of VehicleState UUIDs to retrieve.
     * @return A Map of VehicleStates by their UUIDs.
     */
    Map<UUID, VehicleState> getVehicles(Collection<UUID> vehicleIds);

    /**
     * Save VehicleState to the VehicleStateStore.
     *
     * @param vehicleState VehicleState to save.
     */
    void saveVehicle(VehicleState vehicleState);

    /**
     * Delete VehicleState from the VehicleStateStore.
     *
     * @param vehicleId UUID of VehicleState to delete.
     */
    void deleteVehicle(UUID vehicleId);

    /**
     * Get a set of active VehicleState UUIDs.
     *
     * @return A Set of active VehicleState UUIDs.
     */
    Set<UUID> getActiveVehicleIds();

    /**
     * Add VehicleState UUID to active vehicleState set.
     *
     * @param vehicleState UUID of VehicleState to save.
     */
    void addActiveVehicle(UUID vehicleId);

    /**
     * Remove VehicleState UUID from active vehicleState set.
     *
     * @param vehicleState UUID of VehicleState to remove.
     */
    void removeActiveVehicle(UUID vehicleStateId);

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