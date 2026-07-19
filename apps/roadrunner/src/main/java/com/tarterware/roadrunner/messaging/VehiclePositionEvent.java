package com.tarterware.roadrunner.messaging;

import java.time.Instant;

/**
 * A data transfer object (DTO) representing a snapshot of a vehicle's state at
 * a specific point in time.
 *
 * <p>
 * This record is used as the primary message payload for Kafka topics,
 * facilitating the asynchronous transfer of telemetry data from the simulation
 * runner to the view layer. It contains spatial coordinates, movement vectors,
 * and lifecycle status metadata.
 * </p>
 *
 * @param vehicleId       the unique identifier of the vehicle as a string
 * @param eventTime       the timestamp when the event was generated
 * @param sequenceNumber  a monotonically increasing identifier (typically based
 *                        on simulation epoch) used to detect and discard stale
 *                        or out-of-order messages
 * @param nsLastExec      the execution time in nanoseconds for the last
 *                        calculation cycle
 * @param positionValid   indicates if the current latitude and longitude
 *                        represent a valid coordinate
 * @param positionLimited indicates if the vehicle's movement is currently
 *                        restricted by topology constraints
 * @param latitude        the current geographic latitude in decimal degrees
 * @param longitude       the current geographic longitude in decimal degrees
 * @param heading         the current compass heading of the vehicle in degrees
 * @param speed           the current velocity of the vehicle in meters per
 *                        second
 * @param colorCode       a string representation (e.g., Hex or CSS name) used
 *                        for UI rendering
 * @param managerHost     the identifier of the simulation instance managing
 *                        this vehicle
 * @param status          the lifecycle state of the vehicle; valid values
 *                        include "CREATED", "MOVING", "ARRIVED", and "DELETED"
 *
 * @see com.tarterware.roadrunner.adapters.kafka.KafkaVehicleEventPublisher
 * @see com.tarterware.roadrunner.adapters.kafka.KafkaVehicleEventConsumer
 */
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
        String colorCode,
        String managerHost,
        String status // Use: "CREATED", "MOVING", "ARRIVED", "DELETED"
)
{
}
