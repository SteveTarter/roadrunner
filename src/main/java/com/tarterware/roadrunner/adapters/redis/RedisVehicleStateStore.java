package com.tarterware.roadrunner.adapters.redis;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import com.tarterware.roadrunner.models.VehicleState;
import com.tarterware.roadrunner.ports.VehicleStateStore;

/**
 * Redis-based implementation of the {@link VehicleStateStore} port. *
 * <p>
 * This store utilizes Redis to persist vehicle telemetry and manage the
 * lifecycle of active vehicles in a distributed environment. It provides a
 * "Speed Layer" for the simulation, allowing multiple instances of the
 * application to share the current "latest" view of vehicle states.
 * </p>
 * *
 * <p>
 * Key features include:
 * </p>
 * <ul>
 * <li><b>Vehicle Registry:</b> Uses a Redis Set
 * ({@value #ACTIVE_VEHICLE_REGISTRY}) to track all unique, active vehicle
 * IDs.</li>
 * <li><b>Concurrency Control:</b> Implements a primitive distributed lock
 * mechanism using a Redis Set ({@value #VEHICLE_UPDATE_LOCK_SET}).</li>
 * <li><b>Batch Retrieval:</b> Leverages {@code multiGet} operations for
 * high-performance state lookups for large groups of vehicles.</li>
 * </ul>
 * * @see VehicleStateStore
 * 
 * @see RedisControllerVehicleStateStore
 * @see RedisRunnerVehicleStateStore
 */
public class RedisVehicleStateStore implements VehicleStateStore
{
    /** The Redis key used for the set of all active vehicle UUIDs. */
    public static final String ACTIVE_VEHICLE_REGISTRY = "ActiveVehicleRegistry";

    /**
     * The Redis key used for the set of IDs currently being updated by a simulation
     * instance.
     */
    public static final String VEHICLE_UPDATE_LOCK_SET = "VehicleUpdateLockSet";

    private final RedisTemplate<String, Object> redisTemplate;

    private static final Logger logger = LoggerFactory.getLogger(RedisVehicleStateStore.class);

    /**
     * Constructs a new store with the required {@link RedisTemplate}. * @param
     * redisTemplate the template used for Redis data access
     */
    public RedisVehicleStateStore(RedisTemplate<String, Object> redisTemplate)
    {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public VehicleState getVehicle(UUID vehicleId)
    {
        Object value = redisTemplate.opsForValue().get(getVehicleKey(vehicleId));
        return (value instanceof VehicleState vehicleState) ? vehicleState : null;
    }

    @Override
    public Map<UUID, VehicleState> getVehicles(Collection<UUID> vehicleIds)
    {
        if (vehicleIds == null || vehicleIds.isEmpty())
        {
            return Collections.emptyMap();
        }

        List<UUID> orderedIds = vehicleIds.stream().toList();
        List<String> keys = orderedIds.stream()
                .map(this::getVehicleKey)
                .toList();

        List<Object> values = redisTemplate.opsForValue().multiGet(keys);

        if (values == null || values.isEmpty())
        {
            return Collections.emptyMap();
        }

        Map<UUID, VehicleState> result = new HashMap<>();
        for (int i = 0; i < orderedIds.size(); i++)
        {
            Object value = values.get(i);
            if (value instanceof VehicleState vehicleState)
            {
                result.put(orderedIds.get(i), vehicleState);
            }
        }

        return result;
    }

    @Override
    public void saveVehicle(VehicleState vehicleState)
    {
        if (vehicleState == null || vehicleState.getId() == null)
        {
            throw new IllegalArgumentException("vehicleState and vVhicleState.id must not be null");
        }

        redisTemplate.opsForValue().set(getVehicleKey(vehicleState.getId()), vehicleState);
    }

    @Override
    public void deleteVehicle(UUID vehicleId)
    {
        if (vehicleId == null)
        {
            return;
        }

        String id = vehicleId.toString();

        redisTemplate.opsForSet().remove(ACTIVE_VEHICLE_REGISTRY, id);
        redisTemplate.opsForSet().remove(VEHICLE_UPDATE_LOCK_SET, id);
        redisTemplate.delete(getVehicleKey(vehicleId));
    }

    @Override
    public Set<UUID> getActiveVehicleIds()
    {
        Set<Object> members = redisTemplate.opsForSet().members(ACTIVE_VEHICLE_REGISTRY);
        if (members == null || members.isEmpty())
        {
            return Collections.emptySet();
        }

        Set<UUID> ids = new LinkedHashSet<>();
        for (Object member : members)
        {
            if (member != null)
            {
                ids.add(UUID.fromString(member.toString()));
            }
        }

        return ids;
    }

    @Override
    public void addActiveVehicle(UUID vehicleId)
    {
        if (vehicleId == null)
        {
            return;
        }

        redisTemplate.opsForSet().add(ACTIVE_VEHICLE_REGISTRY, vehicleId.toString());
    }

    @Override
    public void removeActiveVehicle(UUID vehicleId)
    {
        if (vehicleId == null)
        {
            return;
        }

        redisTemplate.opsForSet().remove(ACTIVE_VEHICLE_REGISTRY, vehicleId.toString());
    }

    @Override
    public boolean tryAcquireUpdateLock(UUID vehicleId)
    {
        if (vehicleId == null)
        {
            return false;
        }

        Long added = redisTemplate.opsForSet().add(VEHICLE_UPDATE_LOCK_SET, vehicleId.toString());
        return added != null && added > 0;
    }

    @Override
    public void releaseUpdateLock(UUID vehicleId)
    {
        if (vehicleId == null)
        {
            return;
        }

        redisTemplate.opsForSet().remove(VEHICLE_UPDATE_LOCK_SET, vehicleId.toString());
    }

    @Override
    public long getActiveVehicleCount()
    {
        Long size = redisTemplate.opsForSet().size(ACTIVE_VEHICLE_REGISTRY);
        return size == null ? 0L : size;
    }

    @Override
    public void reset()
    {
        logger.info("Resetting the variables");

        redisTemplate.delete(ACTIVE_VEHICLE_REGISTRY);
        redisTemplate.delete(VEHICLE_UPDATE_LOCK_SET);

        // Delete all of the Vehicle data.
        redisTemplate.delete("{vehicle}:*");
    }

    /**
     * Generates a namespaced Redis key for a vehicle ID. * @param vehicleId the
     * vehicle UUID
     * 
     * @return a formatted string key (e.g.,
     *         "Vehicle:550e8400-e29b-41d4-a716-446655440000")
     */
    private String getVehicleKey(UUID vehicleId)
    {
        return "Vehicle:" + vehicleId;
    }
}