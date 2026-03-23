package com.tarterware.roadrunner.ports;

import java.util.UUID;

import com.tarterware.roadrunner.models.TripPlan;

public interface TripPlanRepository
{

    /**
     * Retrieve TripPlan for Vehicle by its UUID.
     *
     * @param uuid The UUID of the Vehicle.
     * @return The corresponding TripPlan, or null if not found.
     */
    TripPlan getTripPlan(UUID vehicleId);

    /**
     * Save TripPlan for Vehicle by its UUID.
     *
     * @param uuid     The UUID of the Vehicle.
     * @param tripPlan TripPlan to save
     * @return The corresponding TripPlan, or null if not found.
     */
    void saveTripPlan(UUID vehicleId, TripPlan tripPlan);

    /**
     * Delete TripPlan for Vehicle by its UUID.
     *
     * @param uuid The UUID of the Vehicle.
     */
    void deleteTripPlan(UUID vehicleId);

    /**
     * Retrieve TripPlan for Vehicle by its UUID.
     *
     * @param uuid The UUID of the Vehicle.
     * @return true if TripPlan for Vehicle exists.
     */
    boolean exists(UUID vehicleId);

    /**
     * Reset the TripPlanRepository, clearing all resources.
     */
    void reset();
}