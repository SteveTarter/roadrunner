package com.tarterware.roadrunner.messaging;

import java.time.Instant;
import com.tarterware.roadrunner.models.TripPlan;

/**
 * Event published when route lookup is complete to start vehicle simulation.
 */
public record VehicleSimulationStartEvent(
        String vehicleId,
        String username,
        TripPlan tripPlan,
        Instant eventTime
) {}
