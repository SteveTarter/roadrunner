package com.tarterware.roadrunner.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a discrete simulation execution window within the Roadrunner
 * system. *
 * <p>
 * This model is used by the
 * {@link com.tarterware.roadrunner.ports.SimulationRegistry} to track the
 * lifecycle of vehicle simulations. It provides the temporal boundaries (start
 * and end times) necessary for the playback engine to seek specific offsets
 * within Kafka topics.
 * </p>
 * * @see com.tarterware.roadrunner.ports.SimulationRegistry
 */
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimulationSession
{
    /**
     * The unique identifier for the vehicle in this simulation session.
     */
    String id;

    /**
     * The username that created this vehicle.
     */
    String username;

    /**
     * The color associated with this vehicle
     */
    String colorCode;

    /**
     * The precise ms Epoch timestamp when the simulation began.
     */
    Long start;

    /**
     * The precise ms Epoch timestamp when the simulation concluded.
     */
    Long end;
}
