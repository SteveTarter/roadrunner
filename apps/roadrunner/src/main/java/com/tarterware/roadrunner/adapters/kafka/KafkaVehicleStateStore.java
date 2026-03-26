package com.tarterware.roadrunner.adapters.kafka;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.models.VehicleState;
import com.tarterware.roadrunner.ports.VehicleStateStore;

@Component
@ConditionalOnProperty(prefix = "com.tarterware.roadrunner.messaging.kafka", name = "enabled", havingValue = "true")
public class KafkaVehicleStateStore implements VehicleStateStore
{
    private Set<UUID> activeVehicleSet = ConcurrentHashMap.newKeySet();
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
        logger.info("Resetting the Kafka variables");

        activeVehicleSet.clear();
        vehicleStateMap.clear();
    }

}
