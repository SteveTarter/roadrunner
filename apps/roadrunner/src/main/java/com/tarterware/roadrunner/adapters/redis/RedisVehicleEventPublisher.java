package com.tarterware.roadrunner.adapters.redis;

import java.util.UUID;

import com.tarterware.roadrunner.components.Vehicle;
import com.tarterware.roadrunner.ports.VehicleEventPublisher;

public class RedisVehicleEventPublisher implements VehicleEventPublisher
{

    @Override
    public void publishVehicleCreated(Vehicle vehicle)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void publishVehicleUpdated(Vehicle vehicle)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void publishVehicleDeleted(UUID vehicleId)
    {
        // TODO Auto-generated method stub

    }

}
