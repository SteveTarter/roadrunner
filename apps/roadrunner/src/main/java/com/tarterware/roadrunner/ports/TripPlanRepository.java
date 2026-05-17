package com.tarterware.roadrunner.ports;

import com.tarterware.roadrunner.models.TripPlan;

public interface TripPlanRepository
{

    /**
     * Retrieve TripPlan for Vehicle by its ID.
     *
     * @param vehicleId The ID of the Vehicle.
     * @return The corresponding TripPlan, or null if not found.
     */
    TripPlan getTripPlan(String vehicleId);

    /**
     * Save TripPlan for Vehicle by its ID.
     *
     * @param vehicleId The ID of the Vehicle.
     * @param tripPlan  TripPlan to save
     */
    void saveTripPlan(String vehicleId, TripPlan tripPlan);

    /**
     * Delete TripPlan for Vehicle by its ID.
     *
     * @param vehicleId The ID of the Vehicle.
     */
    void deleteTripPlan(String vehicleId);

    /**
     * Retrieve TripPlan for Vehicle by its ID.
     *
     * @param vehicleId The ID of the Vehicle.
     * @return true if TripPlan for Vehicle exists.
     */
    boolean exists(String vehicleId);

    /**
     * Reset the TripPlanRepository, clearing all resources.
     */
    void reset();
}