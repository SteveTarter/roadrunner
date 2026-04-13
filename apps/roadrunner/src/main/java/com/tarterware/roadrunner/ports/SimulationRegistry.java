package com.tarterware.roadrunner.ports;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port interface for managing the persistence and retrieval of simulation session metadata.
 *
 * <p>
 * This registry acts as a specialized state store that tracks the temporal boundaries 
 * of simulation runs. It enables the system to "interrogate" past events, providing 
 * the necessary start and end timestamps required for the playback engine to 
 * perform time-based seeks within Kafka topics.
 * </p>
 *
 * @see com.tarterware.roadrunner.models.SimulationSession
 * @see com.tarterware.roadrunner.adapters.kafka.KafkaVehicleEventPublisher
 */
import com.tarterware.roadrunner.models.SimulationSession;

public interface SimulationRegistry
{
    /**
     * Records the start of a simulation session.
     *
     * @param vehicleID the unique identifier for the vehicle in the simulation
     *                  session
     * @param startTime the precise timestamp when the simulation began
     */
    void recordStart(UUID vehicleID, Instant startTime);

    /**
     * Records the conclusion of a simulation session by updating its end timestamp.
     *
     * @param vehicleID the unique identifier for the vehicle in the simulation
     *                  session
     * @param endTime   the precise timestamp when the simulation concluded
     */
    void recordEnd(UUID vehicleID, Instant startTime);

    /**
     * Retrieves a complete history of all recorded simulation sessions.
     *
     * @return a list of {@link SimulationSession} objects representing both
     *         historical and active simulations.
     */
    List<SimulationSession> getAllSessions();
}
