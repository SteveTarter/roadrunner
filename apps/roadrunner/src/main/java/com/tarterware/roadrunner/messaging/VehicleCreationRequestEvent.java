package com.tarterware.roadrunner.messaging;

import java.time.Instant;
import com.tarterware.roadrunner.models.TripPlan;

/**
 * Event published to request vehicle creation and route lookup.
 */
public record VehicleCreationRequestEvent(
        String vehicleId,
        String username,
        TripPlan tripPlan,
        String colorCode,
        Instant eventTime
) {}
