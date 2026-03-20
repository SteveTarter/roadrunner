package com.tarterware.roadrunner.messaging;

import java.time.Instant;

public record VehiclePositionEvent(
        String vehicleId,
        Instant eventTime,
        long sequenceNumber,
        double latitude,
        double longitude,
        double heading,
        double speed,
        String status
)
{
}
