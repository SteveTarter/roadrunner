package com.tarterware.roadrunner.components;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.tarterware.roadrunner.models.TripPlan;
import com.tarterware.roadrunner.services.DirectionsService;

@Component
public class VehicleManager
{
    private long msPeriod = 100;

    private Timer timer;

    private Map<UUID, Vehicle> vehicleMap = 
    		Collections.synchronizedMap(new HashMap<UUID, Vehicle>());
	
	private static final Logger logger = LoggerFactory.getLogger(VehicleManager.class);
	
	@Autowired
	DirectionsService directionsService;
	
	public Map<UUID, Vehicle> getVehicleMap()
	{
		return vehicleMap;
	}
	
	public Vehicle getVehicle(UUID uuid)
	{
		return vehicleMap.get(uuid);
	}
	
    public void startup()
    {
        if(timer != null)
        {
            throw new IllegalStateException("VehicleManager has already started!");
        }
        
        Random random = new Random();
        long msDelay = (long) (msPeriod * random.nextDouble());
        logger.info("Starting VehicleManager with period of " + msPeriod + " ms and delay of " + msDelay + " ms.");;
        timer = new Timer("VehicleManager Timer");
        timer.schedule(new UpdateTask(), msDelay, msPeriod);
    }
    
    public void shutdown()
    {
        if(timer == null)
        {
            throw new IllegalStateException("VehicleManager is not running!");
        }
        
        timer.cancel();
        timer = null;
    }
    
    public boolean isRunning()
    {
        return timer != null;
    }
    
    public long getMsPeriod()
    {
        return msPeriod;
    }
    
    public void setMsPeriod(long value)
    {
        boolean wasRunning = isRunning();
        if(wasRunning)
        {
            shutdown();
        }
        
        msPeriod = value;
        
        if(wasRunning)
        {
            startup();
        }
    }
    
    public UUID createVehicle(TripPlan tripPlan)
    {
    	Vehicle vehicle = new Vehicle(directionsService);
    	vehicle.setTripPlan(tripPlan);
    	vehicleMap.put(vehicle.getId(), vehicle);
    	
    	return vehicle.getId();
    }
    
    class UpdateTask extends TimerTask
    {
        @Override
        public void run()
        {
        	// Loop through each of the Vehicles and update them.
        	for(Vehicle vehicle : vehicleMap.values())
        	{
        		vehicle.update();
        	}
        }
    }
}
