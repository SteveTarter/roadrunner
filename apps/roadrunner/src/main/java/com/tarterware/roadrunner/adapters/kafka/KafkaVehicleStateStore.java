package com.tarterware.roadrunner.adapters.kafka;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tarterware.roadrunner.models.VehicleState;
import com.tarterware.roadrunner.ports.VehicleStateStore;

/**
 * An in-memory implementation of the {@link VehicleStateStore} designed for use
 * with the Kafka messaging adapter. *
 * <p>
 * This class maintains the current state of all vehicles and the set of active
 * vehicle IDs using thread-safe collections to ensure data consistency across
 * multiple Kafka listener threads. Unlike the Redis-based implementation, this
 * store is volatile and serves as a local cache of the latest telemetry
 * received from the event stream.
 * </p>
 * *
 * <p>
 * This component is used as a base for specialized stores that satisfy specific
 * port requirements in the application runner and controller layers.
 * </p>
 * * @see VehicleStateStore
 * 
 * @see KafkaControllerVehicleStateStore
 * @see KafkaSimulationVehicleStateStore
 */
public class KafkaVehicleStateStore implements VehicleStateStore
{
    /** Set of IDs for vehicles currently active in the simulation. */
    private Set<UUID> activeVehicleSet = ConcurrentHashMap.newKeySet();

    /**
     * Map containing the latest known state for each vehicle, keyed by vehicle ID.
     */
    private ConcurrentHashMap<UUID, VehicleState> vehicleStateMap = new ConcurrentHashMap<UUID, VehicleState>();

    private static final Logger logger = LoggerFactory.getLogger(KafkaVehicleStateStore.class);

    @Override
    public VehicleState getVehicle(UUID vehicleId)
    {
        return vehicleStateMap.get(vehicleId);
    }

    @Override
    public Map<UUID, VehicleState> getVehicles(Collection<UUID> vehicleIds)
    {
        if (vehicleIds == null)
        {
            throw new IllegalArgumentException("vehicleIds cannot be null!");
        }

        Map<UUID, VehicleState> resultMap = new HashMap<UUID, VehicleState>();

        for (UUID vehicleId : vehicleIds)
        {
            VehicleState vehicleState = getVehicle(vehicleId);
            resultMap.put(vehicleId, vehicleState);
        }

        return resultMap;
    }

    @Override
    public void saveVehicle(VehicleState vehicleState)
    {
        if (vehicleState == null)
        {
            throw new IllegalArgumentException("vehicleState cannot be null!");
        }

        if (vehicleState.getId() == null)
        {
            throw new IllegalArgumentException("vehicleState cannot have a null id!");
        }

        vehicleStateMap.put(vehicleState.getId(), vehicleState);
    }

    @Override
    public void deleteVehicle(UUID vehicleId)
    {
        if (vehicleId == null)
        {
            throw new IllegalArgumentException("vehicleId cannot be null!");
        }

        vehicleStateMap.remove(vehicleId);
        activeVehicleSet.remove(vehicleId);
    }

    @Override
    public Set<UUID> getActiveVehicleIds()
    {
        return activeVehicleSet;
    }

    @Override
    public void addActiveVehicle(UUID vehicleId)
    {
        activeVehicleSet.add(vehicleId);
    }

    @Override
    public void removeActiveVehicle(UUID vehicleId)
    {
        activeVehicleSet.remove(vehicleId);
    }

    @Override
    public boolean tryAcquireUpdateLock(UUID vehicleId)
    {
        return true;
    }

    @Override
    public void releaseUpdateLock(UUID vehicleId)
    {
        // No op
    }

    @Override
    public long getActiveVehicleCount()
    {
        return activeVehicleSet.size();
    }

    @Override
    public void reset()
    {
        logger.info("Resetting the variables");

        activeVehicleSet = ConcurrentHashMap.newKeySet();
        vehicleStateMap.clear();
    }

}
