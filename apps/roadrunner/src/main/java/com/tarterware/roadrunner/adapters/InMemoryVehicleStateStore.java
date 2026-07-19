package com.tarterware.roadrunner.adapters;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.tarterware.roadrunner.adapters.kafka.KafkaControllerVehicleStateStore;
import com.tarterware.roadrunner.adapters.kafka.KafkaSimulationVehicleStateStore;
import com.tarterware.roadrunner.models.VehicleState;
import com.tarterware.roadrunner.ports.VehicleStateStore;

/**
 * An in-memory implementation of the {@link InMemoryVehicleStateStore} designed
 * for use with the Kafka messaging adapter. *
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
 * * @see InMemoryVehicleStateStore
 * 
 * @see KafkaControllerVehicleStateStore
 * @see KafkaSimulationVehicleStateStore
 */
public class InMemoryVehicleStateStore implements VehicleStateStore
{
    /** Set of IDs for vehicles currently active in the simulation. */
    private Set<String> activeVehicleSet = ConcurrentHashMap.newKeySet();

    /**
     * Map containing the latest known state for each vehicle, keyed by vehicle ID.
     */
    private ConcurrentHashMap<String, VehicleState> vehicleStateMap = new ConcurrentHashMap<String, VehicleState>();

    @Override
    public VehicleState getVehicle(String vehicleId)
    {
        return vehicleStateMap.get(vehicleId);
    }

    @Override
    public Map<String, VehicleState> getVehicles(Collection<String> vehicleIds)
    {
        if (vehicleIds == null)
        {
            throw new IllegalArgumentException("vehicleIds cannot be null!");
        }

        Map<String, VehicleState> resultMap = new HashMap<String, VehicleState>();

        for (String vehicleId : vehicleIds)
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
    public void deleteVehicle(String vehicleId)
    {
        if (vehicleId == null)
        {
            throw new IllegalArgumentException("vehicleId cannot be null!");
        }

        vehicleStateMap.remove(vehicleId);
        activeVehicleSet.remove(vehicleId);
    }

    @Override
    public Set<String> getActiveVehicleIds()
    {
        return activeVehicleSet;
    }

    @Override
    public void addActiveVehicle(String vehicleId)
    {
        activeVehicleSet.add(vehicleId);
    }

    @Override
    public void removeActiveVehicle(String vehicleId)
    {
        activeVehicleSet.remove(vehicleId);
    }

    @Override
    public boolean tryAcquireUpdateLock(String vehicleId)
    {
        return true;
    }

    @Override
    public void releaseUpdateLock(String vehicleId)
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
        activeVehicleSet = ConcurrentHashMap.newKeySet();
        vehicleStateMap.clear();
    }

}
