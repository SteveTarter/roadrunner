package com.tarterware.roadrunner.messaging;

import java.time.Instant;

public record VehiclePositionEvent(
        String vehicleId,
        Instant eventTime,
        long sequenceNumber,
        long nsLastExec,
        boolean positionValid,
        boolean positionLimited,
        double latitude,
        double longitude,
        double heading,
        double speed,
        String status, // Use: "CREATED", "MOVING", "ARRIVED", "DELETED"
        String managerHost
)
{
}
